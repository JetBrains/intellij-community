package de.plushnikov.refactor;

public class RenameGetterTestUser {

  public static void main(String[] args) {
    RenameGetterTest foo1 = new RenameGetterTest();
    foo1.getSomeString2();
    foo1.setSomeString2("abcd");
  }
}
