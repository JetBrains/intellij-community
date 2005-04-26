package com.intellij.util;

public interface ValueHolder<DataType> {
  DataType getValue();
  void setValue(DataType value);
}
