package de.plushnikov.builder;

import lombok.experimental.Builder;

@Builder
public class BuilderExperimentalExample {
  private String name;
  private int age;

  public static void main(String[] args) {
    BuilderExperimentalExample example = BuilderExperimentalExample.builder().age(123).name("Hallo").build();
    BuilderExperimentalExample.builder().name("fsdf").age(23).name("123123").build();
    System.out.println(example);
  }

}
