package com.converter.forex.controller;

import com.converter.forex.ForexService;
import com.converter.forex.model.ExchangeDirection;
import com.converter.forex.model.ExchangePath;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/forex")
public class ForexController {

    private final ForexService forexService;

    public ForexController(ForexService forexService) {
        this.forexService = forexService;
    }

    @GetMapping("/convert")
    public ExchangePath convert(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        return forexService.exchangeCurrency(amount, from, to);
    }

    @PostMapping("/rates")
    public void registerRate(
            @RequestParam String base,
            @RequestParam String target,
            @RequestParam BigDecimal rate) {
        forexService.addExchangeRate(base, target, rate, ExchangeDirection.BIDIRECTIONAL);
    }

    @PostMapping("/sync")
    public void syncApi(@RequestParam(defaultValue = "USD") String base) {
        forexService.syncRatesFromApi(base);
    }
}