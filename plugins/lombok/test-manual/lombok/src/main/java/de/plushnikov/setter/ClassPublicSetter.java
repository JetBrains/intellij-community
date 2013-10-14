package de.plushnikov.setter;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Setter(AccessLevel.PUBLIC)
public class ClassPublicSetter {
    private int intProperty;
    private float floatProperty;

    private final int finalProperty = 0;
    private static int staticProperty;

    @Getter(AccessLevel.NONE)
    private int noAccessProperty;
}
