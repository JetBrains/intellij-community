package de.plushnikov.refactor;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

public class RenameGetterTest {
  @Getter
  @Setter
  @Accessors(prefix = "f")
  private String fSomeString2;

  public static RenameGetterTest factoryMethod() {
    RenameGetterTest foo = new RenameGetterTest();
    foo.getSomeString2();
    foo.setSomeString2("abcd");
    return foo;
  }

  private static class Inner {
    public void doIt() {
      RenameGetterTest foo1 = new RenameGetterTest();
      foo1.getSomeString2();
      foo1.setSomeString2("abcd");
    }
  }

  private class InnerNonStatic {
    public void doIt() {
      getSomeString2();
      setSomeString2("abcd");
    }
  }

  public static void main(String[] args) {
    RenameGetterTest foo2 = new RenameGetterTest();
    foo2.getSomeString2();
    foo2.setSomeString2("abcd");
  }
}
