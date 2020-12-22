package de.plushnikov.usages;

import lombok.experimental.Delegate;

public class SomeClassB {

  @Delegate
  private SomeClassA someClassA;

  public static void main(String[] args) {
    SomeClassB someClassB = new SomeClassB();
    System.out.println(someClassB.doStuff(123));
  }
}
