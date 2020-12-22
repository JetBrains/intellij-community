package de.plushnikov.constructor;

import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

@RequiredArgsConstructor
public class Issue569 {
  private final Integer I; //error
  private final Supplier<Integer> supplier = () -> I;

//  public Issue569(Integer i) {
//    this.I = i;
//  }

  public static void main(String[] args) {
  }
}
