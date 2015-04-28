package de.plushnikov.getter;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class GetterName {
    private double dValue;

    public static void main(String[] args) {
        GetterName name = new GetterName();
        name.getDValue();
        name.setDValue(123.0);
        System.out.println(name);
    }
}
