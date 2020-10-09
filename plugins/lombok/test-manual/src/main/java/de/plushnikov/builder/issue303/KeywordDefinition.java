package de.plushnikov.builder.issue303;

import lombok.Builder;

@Builder
public class KeywordDefinition {
  private String one;
  private int two;

  public static class KeywordDefinitionBuilder2 extends KeywordDefinitionBuilder {
    private float someFloat;
  }

}
