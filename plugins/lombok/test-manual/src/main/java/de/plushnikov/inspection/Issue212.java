package de.plushnikov.inspection;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Issue212 {

    private String bar;

    protected Issue212() {
        this.bar = "beep";
    }
}