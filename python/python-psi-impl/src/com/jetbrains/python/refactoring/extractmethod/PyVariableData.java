package com.jetbrains.python.refactoring.extractmethod;

import com.intellij.refactoring.util.AbstractVariableData;
import org.jetbrains.annotations.Nullable;

public class PyVariableData extends AbstractVariableData {
  public @Nullable String typeName;


  public @Nullable String getTypeName() {
    return typeName;
  }
}
