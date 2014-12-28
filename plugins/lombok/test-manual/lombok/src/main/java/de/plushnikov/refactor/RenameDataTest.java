package de.plushnikov.refactor;

import lombok.Data;

@Data
public class RenameDataTest {
    String data;

    public static RenameDataTest factoryMethod() {
        RenameDataTest foo = new RenameDataTest();
        foo.setData("data");
        System.out.println(foo.getData());
        return foo;
    }
}
