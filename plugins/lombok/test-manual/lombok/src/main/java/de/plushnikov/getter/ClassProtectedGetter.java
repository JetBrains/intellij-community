package de.plushnikov.getter;

import lombok.AccessLevel;
import lombok.Getter;

@Getter(AccessLevel.PROTECTED)
public class ClassProtectedGetter {
    private int intProperty;
    private float floatProperty;

    private final int finalProperty = 0;
    private static int staticProperty;

    @Getter(AccessLevel.NONE)
    private int noAccessProperty;
}
