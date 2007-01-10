package com.intellij.util.xmlb;

public class DefaultSerializationFilter implements SerializationFilter {
  public boolean accepts(Accessor accessor, Object bean) {
    return true;
  }
}
