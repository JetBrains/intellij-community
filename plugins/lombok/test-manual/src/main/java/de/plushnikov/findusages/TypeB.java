package de.plushnikov.findusages;

import lombok.experimental.Delegate;

public class TypeB {
  @Delegate
  private TypeA typeA;

  public static void main(String[] args) {
    TypeB typeB = new TypeB();
    typeB.doStuff();
  }
}
