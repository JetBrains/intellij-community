package de.plushnikov.builder;

import lombok.Builder;

@Builder
public class BuilderExample {
  private String name;
  private int age;

  public static void main(String[] args) {
    BuilderExample builderExample = new BuilderExample("name", 18);

    BuilderExample example = BuilderExample.builder().age(123).name("Hallo").build();
    BuilderExample.builder().name("fsdf").age(23).name("123123").build();
    System.out.println(example);
  }
}
