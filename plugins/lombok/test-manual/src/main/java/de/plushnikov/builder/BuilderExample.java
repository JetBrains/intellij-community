package de.plushnikov.builder;

import lombok.Builder;

@Builder
public class BuilderExample {
  private String name;
  private int age;
  private double price;

  public static void main(String[] args) {
    BuilderExample builderExample = new BuilderExample("name", 18, 100.0);

    BuilderExample example = BuilderExample.builder().age(123).name("Hallo").build();
    BuilderExample.builder().name("fsdf").price(10.0).age(23).name("123123").build();
    System.out.println(example);
  }
}
