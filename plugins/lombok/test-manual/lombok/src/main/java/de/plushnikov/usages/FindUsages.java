package de.plushnikov.usages;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

public class FindUsages {
    @Accessors(prefix = "_")
    @Getter
    @Setter
    private Integer _foo;

    @Getter
    @Setter
    private Integer bar2;

    private Integer baz1;

    public Integer getBaz1() {
        return baz1;
    }

    public void setBaz1(Integer baz1) {
        this.baz1 = baz1;
    }

    private FindUsages() {
        _foo = 10;
        bar2 = 20;
        baz1 = 40;
    }

    public static void main(String[] args) {
        FindUsages m = new FindUsages();
        System.out.println(m.getFoo());
        System.out.println(m.getBar2());
        System.out.println(m.getBaz1());

        m.setFoo(1);
        m.setBar2(2);
        m.setBaz1(4);

        System.out.println(m.getFoo());
        System.out.println(m.getBar2());
        System.out.println(m.getBaz1());
    }
}