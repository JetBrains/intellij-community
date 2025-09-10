package com.jetbrains.python.refactoring.extractmethod;

import com.intellij.refactoring.util.AbstractVariableData;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

public class PyVariableData extends AbstractVariableData {
  public @Nullable String typeName;
  public @Nullable PyType type;


  public @Nullable String getTypeName() {
    return typeName;
  }

  public @Nullable PyType getType() {
    return type;
  }
}
