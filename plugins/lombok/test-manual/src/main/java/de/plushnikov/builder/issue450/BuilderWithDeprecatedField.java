package de.plushnikov.builder.issue450;

import lombok.Singular;

import java.util.Collections;
import java.util.List;

@lombok.Builder
public class BuilderWithDeprecatedField {
  private String bar;

  @Deprecated
  private String foo;

  @Deprecated
  @Singular
  private List<String> xyzs;

  public static void main(String[] args) {
    System.out.println(BuilderWithDeprecatedField.builder().bar("bar").foo("foo").xyzs(Collections.singletonList("yxzx")).clearXyzs().xyz("xyz").build());
  }
}
