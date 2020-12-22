package de.plushnikov.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataAndBuilder {
  private int x;
  private int y;

  public static void main(String[] args) {
//    FooDataAndBuilder instance = new FooDataAndBuilder();
//    System.out.println(instance);
  }
}
