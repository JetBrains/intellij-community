package de.plushnikov.value;

import lombok.Value;

@Value(staticConstructor = "of")
public class ValueWithGeneric176<T> {
    private T name;
    private int count;

    public static void main(String[] args) {
        ValueWithGeneric176<String> valueObject = ValueWithGeneric176.of("thing1", 10);
        System.out.println(valueObject);
    }
}
