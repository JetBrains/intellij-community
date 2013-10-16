package de.plushnikov.equalshashcode;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(doNotUseGetters = true, exclude = {"intProperty"})
public class EqualsAndHashCodeClass {
    private int intProperty;

    private float floatProperty;

    private String stringProperty;
}
