package de.plushnikov.builder.generic.guava;

import com.google.common.collect.ImmutableSortedMap;
import lombok.Singular;

@lombok.Builder
public class SingularGuavaSortedMap<T, F> {
  @Singular
  private ImmutableSortedMap rawTypes;
  @Singular
  private ImmutableSortedMap<Integer, Float> integers;
  @Singular
  private ImmutableSortedMap<T, F> generics;
  @Singular
  private ImmutableSortedMap<? extends Number, ? extends Float> extendsGenerics;

  public static void main(String[] args) {
  }
}
