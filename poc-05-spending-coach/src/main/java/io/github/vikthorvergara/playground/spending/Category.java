package io.github.vikthorvergara.playground.spending;

public enum Category {
    INCOME("money coming in: salary, interest, benefits"),
    HOUSING("rent, mortgage, condominium fees, home repairs"),
    UTILITIES("electricity, water, gas, phone, internet"),
    GROCERIES("supermarkets, bakeries, food shops"),
    DINING("restaurants, cafes, bars, food delivery"),
    TRANSPORT("fuel, ride hailing, public transport, parking, tolls"),
    HEALTH("pharmacies, doctors, gyms, health plans"),
    INSURANCE("car, home, life insurance premiums"),
    SUBSCRIPTIONS("streaming, software, memberships billed on a cycle"),
    SHOPPING("clothes, electronics, online marketplaces, household goods"),
    ENTERTAINMENT("cinema, games, concerts, hobbies"),
    TRAVEL("flights, hotels, car rental, holidays"),
    EDUCATION("courses, school, books for study"),
    FEES_AND_INTEREST("bank fees, card interest, taxes on transactions"),
    SAVINGS_AND_INVESTMENTS("deposits into savings or investment products"),
    CASH("cash withdrawals"),
    GIFTS_AND_DONATIONS("presents, charity, money given to others"),
    TRANSFER("moves between the same person's accounts and card payments"),
    OTHER("anything that fits no category above"),
    UNCATEGORISED("not yet decided");

    private final String definition;

    Category(String definition) {
        this.definition = definition;
    }

    public String definition() {
        return definition;
    }
}
