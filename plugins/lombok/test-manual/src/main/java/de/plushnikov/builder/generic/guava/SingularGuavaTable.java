package de.plushnikov.builder.generic.guava;

import com.google.common.collect.ImmutableTable;
import lombok.Singular;

@lombok.Builder
public class SingularGuavaTable<T, X, Y> {
  @Singular
  private ImmutableTable rawTypes;
  @Singular
  private ImmutableTable<Integer, Float, String> integers;
  @Singular
  private ImmutableTable<T, X, Y> generics;
  @Singular
  private ImmutableTable<? extends Number, ? extends Float, ? extends String> extendsGenerics;

  public static void main(String[] args) {
  }
}
