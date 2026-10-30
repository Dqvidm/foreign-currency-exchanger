package com.converter.forex.model;

import java.math.BigDecimal;
import java.util.List;

public record ExchangePath(
        String from,
        String to,
        BigDecimal initialAmount,
        BigDecimal convertedAmount,
        List<String> route
) {
    public boolean isDirect() {
        return route != null && route.size() == 2;
    }
}