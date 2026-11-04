package com.converter.forex;

import com.converter.forex.model.ExchangeDirection;
import com.converter.forex.model.ExchangePath;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.math.BigDecimal;
import java.util.Locale;

@ShellComponent
@ShellCommandGroup("fx-converter-commands")
public class Commands {

    private final ForexService forexService;

    public Commands(ForexService forexService) {
        this.forexService = forexService;
    }

    @ShellMethod("Fetches live exchange rates from the API")
    public void syncApi(@ShellOption(defaultValue = "USD") String baseCurrency) {
        try {
            forexService.syncRatesFromApi(baseCurrency);
            System.out.printf("Successfully synchronized rates for base currency: %s\n", baseCurrency);
            System.out.println("Test API here: http://localhost:8080/api/forex/convert?amount=100&from=USD&to=EUR\n");
        } catch (Exception e) {
            System.out.println("API Sync failed: " + e.getMessage());
        }
    }

    @ShellMethod("Adds new exchange rates or updates existing ones manually")
    public void registerExchangeRate(
            final String baseCurrency,
            final String exchangeCurrency,
            final BigDecimal exchangeRate) {

        forexService.addExchangeRate(
                baseCurrency,
                exchangeCurrency,
                exchangeRate,
                ExchangeDirection.BIDIRECTIONAL
        );

        System.out.printf("Registered: 1 %s = %s %s\n", baseCurrency, exchangeRate, exchangeCurrency);
    }

    @ShellMethod("Exchanges currencies")
    public void exchange(final BigDecimal amount, final String from, final String to) {
        try {
            ExchangePath result = forexService.exchangeCurrency(amount, from, to);
            System.out.printf("Exchanged %.2f %s -> %.2f %s [Route: %s]\n",
                    result.initialAmount(),
                    result.from(),
                    result.convertedAmount(),
                    result.to(),
                    String.join(" -> ", result.route()));
        } catch (Exception e) {
            System.out.println("Exchange failed: " + e.getMessage());
        }
    }
}