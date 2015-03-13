package de.plushnikov.refactor;

import lombok.Getter;
import lombok.Setter;

public class RenameGetterTest {
    @Getter
    @Setter
    private String someStringS;

    public static RenameGetterTest factoryMethod() {
        RenameGetterTest foo = new RenameGetterTest();
        foo.getSomeStringS();
        foo.setSomeStringS("abcd");
        return foo;
    }

    private static class Inner {
        public void doIt() {
            RenameGetterTest foo1 = new RenameGetterTest();
            foo1.getSomeStringS();
            foo1.setSomeStringS("abcd");
        }
    }

    private class InnerNonStatic {
        public void doIt() {
            getSomeStringS();
            setSomeStringS("abcd");
        }
    }

    public static void main(String[] args) {
        RenameGetterTest foo2 = new RenameGetterTest();
        foo2.getSomeStringS();
        foo2.setSomeStringS("abcd");
    }
}
