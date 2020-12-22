package de.plushnikov.builder;

import lombok.Data;
import lombok.Builder;

@Data
public class BuilderGeneric<T extends Number> {
  private T number;

  @Builder
  public BuilderGeneric(T number) {
    this.number = number;
  }

  public static void main(String[] args) {

    BuilderGeneric<Integer> instance = BuilderGeneric.<Integer>builder().number(234).build();
    System.out.println(instance);
  }
}
