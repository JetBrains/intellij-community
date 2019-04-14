package de.plushnikov.delegate.issue76;

import lombok.experimental.Delegate;

public class TypeB {
  @Delegate
  private TypeA typeA;

  public static void main(String[] args) {
    System.out.println(new TypeB().doStuff("123"));
  }
}
