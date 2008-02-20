package com.intellij.xdebugger.impl.evaluate;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

import java.awt.*;

/**
 * @author nik
 */
public class XQuickEvaluateHandler extends QuickEvaluateHandler {
  public boolean isEnabled(@NotNull final Project project) {
    return false;
  }

  public AbstractValueHint createValueHint(@NotNull final Project project, @NotNull final Editor editor, @NotNull final Point point, final int type) {
    return null;
  }

  public boolean canShowHint(@NotNull final Project project) {
    return false;
  }

  public int getValueLookupDelay() {
    return 700;//todo[nik] use settings
  }
}
