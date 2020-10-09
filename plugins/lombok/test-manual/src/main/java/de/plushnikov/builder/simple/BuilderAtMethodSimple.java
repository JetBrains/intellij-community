package de.plushnikov.builder.simple;

import lombok.Builder;

public class BuilderAtMethodSimple {
  private int myInt;
  private String myString;

  @Builder
  public static BuilderAtMethodSimple simple(int myInt, String myString) {
    BuilderAtMethodSimple methodSimple = new BuilderAtMethodSimple();
    methodSimple.myInt = myInt;
    methodSimple.myString = myString;
    return methodSimple;
  }

  public static void main(String[] args) {
    BuilderAtMethodSimple builderSimple = BuilderAtMethodSimple.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }
}
