package de.plushnikov.builder.singular;

import lombok.Singular;

import java.util.Arrays;

@lombok.Builder
@lombok.experimental.Accessors(prefix = "_")
public class BuilderSingularWithPrefixes {
  @Singular
  private java.util.List<String> _elems;

  public static void main(String[] args) {
    BuilderSingularWithPrefixesBuilder prefixesBuilder = BuilderSingularWithPrefixes.builder();
    System.out.println(Arrays.toString(prefixesBuilder.getClass().getMethods()));
    BuilderSingularWithPrefixes withPrefixes = prefixesBuilder
        .elem("AAAA")
        .elems(Arrays.asList("BBBB", "CCCC"))
        .clearElems()
        .build();
    System.out.println(withPrefixes);
  }
}
