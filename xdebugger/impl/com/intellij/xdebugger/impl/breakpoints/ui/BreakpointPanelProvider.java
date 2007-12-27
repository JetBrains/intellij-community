package com.intellij.xdebugger.impl.breakpoints.ui;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

/**
 * @author nik
 */
public abstract class BreakpointPanelProvider {

  @NotNull
  public abstract AbstractBreakpointPanel[] getBreakpointPanels(@NotNull Project project, @NotNull DialogWrapper parentDialog);

  public abstract void onDialogClosed(final Project project);

}
