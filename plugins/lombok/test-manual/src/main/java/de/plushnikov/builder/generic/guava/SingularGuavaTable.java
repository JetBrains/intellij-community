package de.plushnikov.builder.generic.guava;

import com.google.common.collect.ImmutableTable;
import lombok.Singular;

import java.util.Map;

public class SingularGuavaTable<T, X, Y> {
  @Singular
  private ImmutableTable rawTypes;
  @Singular
  private ImmutableTable<Integer, Float, String> integers;
  @Singular
  private ImmutableTable<T, X, Y> generics;
  @Singular
  private ImmutableTable<? extends Number, ? extends Float, ? extends String> extendsGenerics;

  @java.beans.ConstructorProperties({"rawTypes", "integers", "generics", "extendsGenerics"})
  SingularGuavaTable(ImmutableTable rawTypes, ImmutableTable<Integer, Float, String> integers, ImmutableTable<T, X, Y> generics, ImmutableTable<? extends Number, ? extends Float, ? extends String> extendsGenerics) {
    this.rawTypes = rawTypes;
    this.integers = integers;
    this.generics = generics;
    this.extendsGenerics = extendsGenerics;
  }

  public static void main(String[] args) {
  }

  public static <T, X, Y> SingularGuavaTableBuilder<T, X, Y> builder() {
    return new SingularGuavaTableBuilder<T, X, Y>();
  }

  public static class SingularGuavaTableBuilder<T, X, Y> {
    private ImmutableTable.Builder<Object, Object, Object> rawTypes;
    private ImmutableTable.Builder<Integer, Float, String> integers;
    private ImmutableTable.Builder<T, X, Y> generics;
    private ImmutableTable.Builder<Number, Float, String> extendsGenerics;

    SingularGuavaTableBuilder() {
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> rawType() {
      if (this.rawTypes == null) this.rawTypes = ImmutableTable.builder();
      this.rawTypes.put(rawType$key, rawType$value);
      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> rawTypes(Map rawTypes) {
      if (this.rawTypes == null) this.rawTypes = ImmutableTable.builder();
      this.rawTypes.putAll(rawTypes);
      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> clearRawTypes() {
      this.rawTypes = null;

      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> integer() {
      if (this.integers == null) this.integers = ImmutableTable.builder();
      this.integers.put(integer$key, integer$value);
      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> integers(Map integers) {
      if (this.integers == null) this.integers = ImmutableTable.builder();
      this.integers.putAll(integers);
      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> clearIntegers() {
      this.integers = null;

      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> generic() {
      if (this.generics == null) this.generics = ImmutableTable.builder();
      this.generics.put(generic$key, generic$value);
      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> generics(Map generics) {
      if (this.generics == null) this.generics = ImmutableTable.builder();
      this.generics.putAll(generics);
      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> clearGenerics() {
      this.generics = null;

      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> extendsGeneric() {
      if (this.extendsGenerics == null) this.extendsGenerics = ImmutableTable.builder();
      this.extendsGenerics.put(extendsGeneric$key, extendsGeneric$value);
      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> extendsGenerics(Map extendsGenerics) {
      if (this.extendsGenerics == null) this.extendsGenerics = ImmutableTable.builder();
      this.extendsGenerics.putAll(extendsGenerics);
      return this;
    }

    public SingularGuavaTable.SingularGuavaTableBuilder<T, X, Y> clearExtendsGenerics() {
      this.extendsGenerics = null;

      return this;
    }

    public SingularGuavaTable<T, X, Y> build() {
      return new SingularGuavaTable<T, X, Y>(rawTypes.build(), integers.build(), generics.build(), extendsGenerics.build());
    }

    public String toString() {
      return "de.plushnikov.builder.generic.guava.SingularGuavaTable.SingularGuavaTableBuilder(rawTypes=" + this.rawTypes + ", integers=" + this.integers + ", generics=" + this.generics + ", extendsGenerics=" + this.extendsGenerics + ")";
    }
  }
}
