package com.jetbrains.python.psi;

import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IReparseableElementType;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

public abstract class PyReparseableElementType extends IReparseableElementType implements ICompositeElementType {

  public PyReparseableElementType(@NotNull String debugName) {
    super(debugName, PythonLanguage.INSTANCE);
  }
}
