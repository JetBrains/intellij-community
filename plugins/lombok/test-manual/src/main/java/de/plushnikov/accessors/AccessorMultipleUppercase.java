package de.plushnikov.accessors;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true, chain = true, prefix = {"f", ""})
public class AccessorMultipleUppercase {

  private String flowerField = "x";
  private String fUPPERField = "y";
  private String flUpperField = "z";

  public static void main(String[] args) {
    AccessorMultipleUppercase test = new AccessorMultipleUppercase();

    System.out.println(test.flowerField());
    System.out.println(test.uPPERField());
    System.out.println(test.flUpperField());
  }
}
