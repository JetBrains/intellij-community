package de.plushnikov.constructor;

import lombok.AllArgsConstructor;

@AllArgsConstructor(staticName = "of")
public class AllArgsConstructorClass {
  private int intProperty;

  private float floatProperty;

  private String stringProperty;

  public static void main(String[] args) {
    AllArgsConstructorClass constructorClass = new AllArgsConstructorClass(1, 2.2f, "");
    AllArgsConstructorClass of = AllArgsConstructorClass.of(12, 0.1f, "ddd");
  }
}
