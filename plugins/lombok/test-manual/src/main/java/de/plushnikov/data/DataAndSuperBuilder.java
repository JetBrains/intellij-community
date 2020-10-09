package de.plushnikov.data;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class DataAndSuperBuilder {
    private int x;
    private int y;

  public static void main(String[] args) {
//    FooDataAndSuperBuilder instance = new FooDataAndSuperBuilder();
//    System.out.println(instance);
  }
}
