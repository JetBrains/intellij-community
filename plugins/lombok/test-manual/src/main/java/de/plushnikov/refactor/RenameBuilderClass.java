package de.plushnikov.refactor;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;

@Builder
@Getter
@Setter
public class RenameBuilderClass {
  private int intFielda;

  public static void main(String[] args) {
    RenameBuilderClass builderClass1 = new RenameBuilderClass(2);
    builderClass1.getIntFielda();

    RenameBuilderClass builderClass = RenameBuilderClass.builder().intFielda(123).build();
    System.out.println(builderClass);
  }
}
