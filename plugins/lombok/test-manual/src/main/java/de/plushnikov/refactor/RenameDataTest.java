package de.plushnikov.refactor;

import lombok.Data;

@Data
public class RenameDataTest {
  String someDataField;

  public static RenameDataTest factoryMethod() {
    RenameDataTest foo = new RenameDataTest();
    foo.setSomeDataField("data");
    System.out.println(foo.getSomeDataField());
    return foo;
  }
}
