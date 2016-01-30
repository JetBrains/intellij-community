package de.plushnikov.setter;

import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Accessors(fluent = true)
public class FluentSetterOverride {
    private String field;

    public void init() {
        field("value");
    }

    public FluentSetterOverride field(String field) {
        this.field = field;
        return this;
    }
}
