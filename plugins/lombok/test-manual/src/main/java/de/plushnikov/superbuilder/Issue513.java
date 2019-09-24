package de.plushnikov.superbuilder;

import lombok.experimental.SuperBuilder;

import java.util.Optional;

public class Issue513 {
  @SuperBuilder
  public static class A {
    public String field1;
  }

  @SuperBuilder
  public static class B extends A {
    public String field2;
  }

  public static class OtherClass {
    public String method(int something) {
      return Integer.toOctalString(something);
    }
  }

  public static void main(String[] args) {
    Optional<String> someOptional = Optional.of(args[0]);
    String value = "value";
    OtherClass otherClass = new OtherClass();
    int something = 1;

    A a = someOptional
      .map(item -> (A) A.builder().field1(value).build())
      .orElseGet(() -> A.builder().field1(value).build());
    System.out.println(a);
  }
}
