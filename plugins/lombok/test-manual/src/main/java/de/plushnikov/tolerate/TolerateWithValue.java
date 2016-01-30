package de.plushnikov.tolerate;

import lombok.Value;
import lombok.experimental.Tolerate;

@Value
public class TolerateWithValue {

    int age;
    String name;

    @Tolerate
    public TolerateWithValue(int age) {
        this(age, "unknown");
    }


    public static void main(String[] args) {
        TolerateWithValue test = new TolerateWithValue(12);
        System.out.println(test);
    }
}