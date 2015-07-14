package de.plushnikov.builder.importbuilder.otherpackage;

import lombok.Builder;

public class Builder2Import {
    @Builder
    public Builder2Import(String var) {
        this.var = var;
    }

    private final String var;
   /* more vars */
}
