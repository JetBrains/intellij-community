package com.jetbrains.performancePlugin.lang.psi;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.performancePlugin.lang.IJPerfLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class IJPerfElementType extends IElementType {

  public IJPerfElementType(@NotNull @NonNls String debugName) {
    super(debugName, IJPerfLanguage.INSTANCE);
  }
}
