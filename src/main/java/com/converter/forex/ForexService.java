package com.converter.forex;

import com.converter.forex.model.ExchangeDirection;
import com.converter.forex.model.ExchangePath;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.converter.forex.utils.ObjectMapperBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ForexService {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final int FINAL_SCALE = 4;

    //list mapping Source -> (target -> rate)
    private final ConcurrentMap<String, ConcurrentMap<String, BigDecimal>> graph = new ConcurrentHashMap<>();

    public Map<String, Map<String, BigDecimal>> getExchangeRates() {
        Map<String, Map<String, BigDecimal>> snapshot = new HashMap<>();
        graph.forEach((src, targets) -> snapshot.put(src, Map.copyOf(targets)));
        return Collections.unmodifiableMap(snapshot);
    }

    public void addExchangeRate(String from, String to, BigDecimal rate, ExchangeDirection direction) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Rate must be strictly positive.");
        }

        String source = from.trim().toUpperCase();
        String target = to.trim().toUpperCase();

        if (source.equals(target)) {
            throw new IllegalArgumentException("Source and target currencies cannot be identical.");
        }

        //add rate
        graph.computeIfAbsent(source, k -> new ConcurrentHashMap<>()).put(target, rate);

        //adds reciprocal rate if bidirectional
        if (direction == ExchangeDirection.BIDIRECTIONAL) {
            BigDecimal reciprocal = BigDecimal.ONE.divide(rate, MATH_CONTEXT);
            graph.computeIfAbsent(target, k -> new ConcurrentHashMap<>()).put(source, reciprocal);
        }
    }

    public ExchangePath exchangeCurrency(BigDecimal amount, String from, String to) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative.");
        }

        String source = from.trim().toUpperCase();
        String target = to.trim().toUpperCase();

        //same currency exchange
        if (source.equals(target)) {
            return new ExchangePath(source, target, amount, amount.setScale(FINAL_SCALE, RoundingMode.HALF_UP), List.of(source));
        }

        //checks existence
        if (!graph.containsKey(source) || !graph.containsKey(target)) {
            throw new IllegalArgumentException(String.format("Currencies '%s' or '%s' are missing from the exchange graph.", source, target));
        }

        //bfs (breadth first search... god knows how to write that...)
        List<String> route = findShortestRoute(source, target);
        if (route.isEmpty()) {
            throw new IllegalStateException(String.format("Cannot exchange from '%s' to '%s'. No path exists.", source, target));
        }

        //calc. total converted amount
        BigDecimal finalAmount = calculateAcrossRoute(amount, route);

        return new ExchangePath(source, target, amount, finalAmount, route);
    }

    private List<String> findShortestRoute(String source, String target) {
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> predecessors = new HashMap<>();

        queue.add(source);
        visited.add(source);

        boolean found = false;

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(target)) {
                found = true;
                break;
            }

            Map<String, BigDecimal> neighbors = graph.get(current);
            if (neighbors != null) {
                for (String neighbor : neighbors.keySet()) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        predecessors.put(neighbor, current);
                        queue.add(neighbor);
                    }
                }
            }
        }

        if (!found) {
            return Collections.emptyList();
        }

        // reconstruct path by walking backward from the target
        LinkedList<String> route = new LinkedList<>();
        String step = target;
        while (step != null) {
            route.addFirst(step);
            step = predecessors.get(step);
        }

        return route;
    }

    private BigDecimal calculateAcrossRoute(BigDecimal amount, List<String> route) {
        BigDecimal currentAmount = amount;

        for (int i = 0; i < route.size() - 1; i++) {
            String from = route.get(i);
            String to = route.get(i + 1);

            BigDecimal rate = graph.get(from).get(to);
            if (rate == null) {
                throw new IllegalStateException("Graph consistency error: Missing rate during calculation for " + from + " -> " + to);
            }

            currentAmount = currentAmount.multiply(rate, MATH_CONTEXT);
        }

        // only round at the end
        return currentAmount.setScale(FINAL_SCALE, RoundingMode.HALF_UP);
    }

    private final ObjectMapper objectMapper = ObjectMapperBuilder.buildDefault();
    private final HttpClient httpClient = HttpClient.newHttpClient();


    public void syncRatesFromApi(String baseCurrency) {
        String base = baseCurrency.trim().toUpperCase();

        String apiUrl = "https://open.er-api.com/v6/latest/" + base;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("API responded with status: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode rates = root.path("rates");

            if (rates.isMissingNode()) {
                throw new RuntimeException("Invalid JSON response: 'rates' node missing.");
            }

            //parse rates and add to graph
            rates.fields().forEachRemaining(entry -> {
                String targetCurrency = entry.getKey();

                // Skip mapping the base currency to itself
                if (base.equals(targetCurrency)) {
                    return;
                }

                BigDecimal rate = new BigDecimal(entry.getValue().asText());

                //add rate as bidirectional so u can exchange backwards
                addExchangeRate(base, targetCurrency, rate, ExchangeDirection.BIDIRECTIONAL);
            });

        } catch (Exception e) {
            throw new RuntimeException("Failed to sync rates: " + e.getMessage(), e);
        }
    }
}