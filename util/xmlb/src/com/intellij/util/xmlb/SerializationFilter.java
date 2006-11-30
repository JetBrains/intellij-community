package com.intellij.util.xmlb;

public interface SerializationFilter {
    boolean accepts(Accessor accessor, Object bean);
}
