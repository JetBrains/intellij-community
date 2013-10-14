package de.plushnikov;

import lombok.AccessLevel;
import lombok.FluentSetter;

public class FluentFieldSetter {
    @FluentSetter
    private int intProperty;

    @FluentSetter(AccessLevel.PUBLIC)
    private int publicProperty;

    @FluentSetter(AccessLevel.PROTECTED)
    private int protectedProperty;

    @FluentSetter(AccessLevel.PACKAGE)
    private int packageProperty;

    @FluentSetter(AccessLevel.PRIVATE)
    private Integer privateProperty;

    @FluentSetter(AccessLevel.NONE)
    private int noAccessProperty;

    public static void main(String[] args) {
        FluentFieldSetter setter = new FluentFieldSetter();
        setter.intProperty(123).packageProperty(12321);
        System.out.println(setter.intProperty);
    }
}
