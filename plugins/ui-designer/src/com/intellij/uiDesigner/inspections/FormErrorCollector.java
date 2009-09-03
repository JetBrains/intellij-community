package com.intellij.uiDesigner.inspections;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.IComponent;

/**
 * @author yole
 */
public abstract class FormErrorCollector {
  public abstract void addError(final String inspectionId, final IComponent component, @Nullable IProperty prop,
                                @NotNull String errorMessage,
                                @Nullable EditorQuickFixProvider editorQuickFixProvider);
}
