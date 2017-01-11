package de.plushnikov.delegate;

import lombok.experimental.Delegate;

import java.util.ArrayList;

public class DelegateRightType<T> {
  //    @Delegate
  private T delegatorGeneric;

  //    @Delegate
  private int delegatorPrimitive;

  //    @Delegate
  private int[] delegatorPrimitiveArray;

  //    @Delegate
  private Integer[] delegatorArray;

  //    @Delegate
  private Integer[] delegatorArray() {
    return delegatorArray;
  }

  @Delegate
  private Integer delegatorInteger = 0;

  public static void main(String[] args) {
    DelegateRightType<ArrayList> test = new DelegateRightType<ArrayList>();
    test.compareTo(0);
  }
}
