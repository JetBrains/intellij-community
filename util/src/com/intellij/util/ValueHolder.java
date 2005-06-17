package com.intellij.util;

public interface ValueHolder<DataType, DataHolderType> {
  DataType getValue(final DataHolderType dataHolder);
  void setValue(DataType value, final DataHolderType dataHolder);
}
