package de.plushnikov.refactor;

public class RenameGetterTestUser {

    public static void main(String[] args) {
        RenameGetterTest foo1 = new RenameGetterTest();
        foo1.getSomeStringS();
        foo1.setSomeStringS("abcd");
    }
}
