package de.plushnikov.builder.simple;

public class BuilderAtConstructorSimplePredefined {
  private int myInt;
  private String myString;

  @lombok.Builder
  public BuilderAtConstructorSimplePredefined(int myInt, String myString) {
    this.myInt = myInt + 1;
    this.myString = myString;
  }

  static class BuilderAtConstructorSimplePredefinedBuilder {
    private int myInt;

    public BuilderAtConstructorSimplePredefined.BuilderAtConstructorSimplePredefinedBuilder myString(String myString) {
      this.myString = myString + "something";
      return this;
    }
  }

  public static void main(String[] args) {
    BuilderAtConstructorSimplePredefined builderSimple = BuilderAtConstructorSimplePredefined.builder().myInt(123).myString("string").build();
    System.out.println(builderSimple);
  }
}
