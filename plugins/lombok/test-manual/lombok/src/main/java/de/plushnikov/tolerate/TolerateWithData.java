package de.plushnikov.tolerate;

import lombok.Data;
import lombok.Value;
import lombok.experimental.Tolerate;

@Data
public class TolerateWithData {
    private final int age;
    private final String name;

    @Tolerate
    public TolerateWithData(int age) {
        this(age, "unknown");
    }

    public static void main(String[] args) {
        TolerateWithData test = new TolerateWithData(12);
        System.out.println(test);
    }
}