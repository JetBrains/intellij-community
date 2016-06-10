package de.plushnikov.builder.generic.guava;

import com.google.common.collect.ImmutableBiMap;
import lombok.Singular;

@lombok.Builder
public class SingularGuavaBiMap<T, F> {
  @Singular
  private ImmutableBiMap rawTypes;
  @Singular
  private ImmutableBiMap<Integer, Float> integers;
  @Singular
  private ImmutableBiMap<T, F> generics;
  @Singular
  private ImmutableBiMap<? extends Number, ? extends Float> extendsGenerics;

  public static void main(String[] args) {
  }
}
