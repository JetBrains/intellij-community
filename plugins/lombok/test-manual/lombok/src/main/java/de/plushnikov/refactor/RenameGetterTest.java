package de.plushnikov.refactor;

import lombok.Getter;

@Getter
public class RenameGetterTest {
    private String someString;

    public static RenameGetterTest factoryMethod() {
        RenameGetterTest foo = new RenameGetterTest();
        foo.getSomeString();
        return foo;
    }
}
