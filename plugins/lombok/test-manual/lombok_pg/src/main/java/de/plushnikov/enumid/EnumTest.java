package de.plushnikov.enumid;

public class EnumTest {
    public static void main(String[] args) {
        EnumIdExample.Status x = EnumIdExample.Status.COMPLETED;
        System.out.println(EnumIdExample.Status.findByCode(0));

        AnotherStatus y = AnotherStatus.READY;
        System.out.println(AnotherStatus.findByCode(1));
    }
}
