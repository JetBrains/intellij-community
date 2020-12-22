package de.plushnikov.wither;

import java.util.Arrays;

class WitherWithGenerics<T, J extends T, L extends Number> {
  @lombok.experimental.Wither
  J test;
  @lombok.experimental.Wither
  java.util.List<L> test2;
  @lombok.experimental.Wither
  java.util.List<? extends L> test3;
  int $i;

  public WitherWithGenerics(J test, java.util.List<L> test2, java.util.List<? extends L> test3) {
  }

  public static void main(String[] args) {
    new WitherWithGenerics<Number, Float, Long>(1.0f, Arrays.asList(2L), Arrays.asList(3L))
        .withTest(Float.MAX_VALUE)
        .withTest2(Arrays.asList(3L))
        .withTest3(Arrays.asList(4L));
  }
}