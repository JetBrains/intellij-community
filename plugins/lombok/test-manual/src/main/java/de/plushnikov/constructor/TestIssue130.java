package de.plushnikov.constructor;

import lombok.AllArgsConstructor;

public class TestIssue130 {
  @AllArgsConstructor
  public static class Parent {
    private String name;
  }

  //    @AllArgsConstructor
  public static class Subclass extends Parent {
    private String value;

    public Subclass(String name, String value) {
      super(name);
    }
  }

  public static void main(String[] args) {
    Subclass subclass = new Subclass("name", "value");
  }
}
