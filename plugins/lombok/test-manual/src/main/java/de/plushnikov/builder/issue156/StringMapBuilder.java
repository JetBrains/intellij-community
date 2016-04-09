package de.plushnikov.builder.issue156;

import lombok.Builder;
import lombok.Singular;

import java.util.HashMap;
import java.util.Map;

public class StringMapBuilder<R> extends HashMap<String, R> {

  @Builder
  StringMapBuilder(@Singular Map<String, ? extends R> entries) {
    super(entries);
  }

  public static void main(String[] args) {

  }
}
