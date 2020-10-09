package de.plushnikov.builder;

import lombok.Builder;

public class BuilderComplex {
  @Builder(buildMethodName = "execute")
  private static <T extends Number> void testVoidWithGenerics(T number, int arg2, String arg3, BuilderComplex selfRef) {
  }

  public static void main(String[] args) {
    VoidBuilder<Integer> builder = BuilderComplex.<Integer>builder();
    builder.number(12).arg2(32).arg3("sss").execute();
  }
}
