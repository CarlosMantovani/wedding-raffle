package com.weddingraffle.rifa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "raffle_combo")
public class RaffleCombo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private boolean highlightMostChosen;

    @Column(nullable = false)
    private boolean highlightBestValue;

    protected RaffleCombo() {}

    public RaffleCombo(Integer quantity, BigDecimal price, boolean active, Integer displayOrder) {
        this(quantity, price, active, displayOrder, false, false);
    }

    public RaffleCombo(
            Integer quantity,
            BigDecimal price,
            boolean active,
            Integer displayOrder,
            boolean highlightMostChosen,
            boolean highlightBestValue) {
        this.quantity = quantity;
        this.price = price;
        this.active = active;
        this.displayOrder = displayOrder;
        this.highlightMostChosen = highlightMostChosen;
        this.highlightBestValue = highlightBestValue;
    }

    public Long getId() {
        return id;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public boolean isHighlightMostChosen() {
        return highlightMostChosen;
    }

    public boolean isHighlightBestValue() {
        return highlightBestValue;
    }

    public void update(
            BigDecimal price,
            boolean active,
            Integer displayOrder,
            boolean highlightMostChosen,
            boolean highlightBestValue) {
        this.price = price;
        this.active = active;
        this.displayOrder = displayOrder;
        this.highlightMostChosen = highlightMostChosen;
        this.highlightBestValue = highlightBestValue;
    }
}
