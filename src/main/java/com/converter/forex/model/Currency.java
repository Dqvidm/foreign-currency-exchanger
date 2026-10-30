package com.converter.forex.model;

import java.math.BigDecimal;

public record Currency(String name, BigDecimal unitValue) implements Comparable<Currency> {

    public Currency {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Currency name cannot be null or blank.");
        }
        name = name.trim().toUpperCase();
    }

    @Override
    public int compareTo(Currency other) {
        return this.name.compareTo(other.name);
    }
}