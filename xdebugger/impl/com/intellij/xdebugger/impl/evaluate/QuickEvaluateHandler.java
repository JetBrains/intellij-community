package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author nik
 */
public abstract class QuickEvaluateHandler {

  public abstract boolean isEnabled(@NotNull Project project);

  public abstract AbstractValueHint createValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, int type);

  public abstract boolean canShowHint(@NotNull Project project);

  public abstract int getValueLookupDelay();
}
