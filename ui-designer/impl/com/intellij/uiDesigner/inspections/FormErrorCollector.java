package com.intellij.uiDesigner.inspections;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.uiDesigner.lw.IProperty;

/**
 * @author yole
 */
public abstract class FormErrorCollector {
  public abstract void addError(final String inspectionId,
                                @Nullable IProperty prop,
                                @NotNull String errorMessage,
                                @Nullable EditorQuickFixProvider editorQuickFixProvider);
}
