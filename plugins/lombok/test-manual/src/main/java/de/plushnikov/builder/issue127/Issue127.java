package de.plushnikov.builder.issue127;

import lombok.Builder;

@Builder
public class Issue127<T> {
  private T body;
}
