package com.jetbrains.performancePlugin.lang.psi;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.performancePlugin.lang.IJPerfLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class IJPerfTokenType extends IElementType {

  public IJPerfTokenType(@NotNull @NonNls String debugName) {
    super(debugName, IJPerfLanguage.INSTANCE);
  }

  @Override
  public String toString() {
    return "PerformanceTestTokenType." + super.toString();
  }
}
