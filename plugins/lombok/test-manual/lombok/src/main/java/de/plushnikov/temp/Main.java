package de.plushnikov.temp;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

public class Main {
    @Accessors(prefix = "_") @Getter @Setter private Integer _foo;
    @Accessors(prefix = "f") @Getter @Setter private Integer fBar;
    @Getter @Setter private Integer baz;

    private Main() {
        _foo = 10;
        fBar = 20;
        baz = 40;
    }

    public static void main(String[] args) {
        Main m = new Main();
        System.out.println(m.getFoo());
        System.out.println(m.getBar());
        System.out.println(m.getBaz());

        m.setFoo(1);
        m.setBar(2);
        m.setBaz(4);

        System.out.println(m.getFoo());
        System.out.println(m.getBar());
        System.out.println(m.getBaz());
    }
}