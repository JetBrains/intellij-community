package de.plushnikov.builder;

import lombok.Builder;
import lombok.ToString;

@Builder
@ToString
public class BuilderPredefined {
  private String name;
  private int age;

  public static class FirstInnerClassDefined {
    private boolean injectHere = false;
  }

  public static class BuilderPredefinedBuilder {
    private String name;

    public void age(int age) {
      this.age = age;
    }
  }

  public static void main(String[] args) {
    BuilderPredefinedBuilder builder = BuilderPredefined.builder();
    builder.name("Mascha").age(172);
    BuilderPredefined result = builder.build();
    System.out.println(result.toString());
  }
}