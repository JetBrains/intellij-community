package de.plushnikov.value;

import lombok.Value;

@Value
public class Foo {
  String one;
  String two = "fooqwewe";

  public static void main(String[] args) {
    System.out.println(new Foo("one"));
  }
}