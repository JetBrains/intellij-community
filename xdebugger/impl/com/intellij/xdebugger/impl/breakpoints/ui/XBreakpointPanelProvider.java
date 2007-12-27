package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XBreakpointPanelProvider extends BreakpointPanelProvider {
  @NotNull
  public AbstractBreakpointPanel[] getBreakpointPanels(@NotNull final Project project, @NotNull final DialogWrapper parentDialog) {
    XBreakpointType<?,?>[] types = XBreakpointType.getBreakpointTypes();
    AbstractBreakpointPanel[] panels = new AbstractBreakpointPanel[types.length];
    for (int i = 0; i < types.length; i++) {
      panels[i] = createBreakpointsPanel(project, parentDialog, types[i]);
    }
    return panels;
  }

  private static <B extends XBreakpoint<?>> XBreakpointsPanel<B> createBreakpointsPanel(final Project project, DialogWrapper parentDialog, final XBreakpointType<B, ?> type) {
    return new XBreakpointsPanel<B>(project, parentDialog, type);
  }

  public void onDialogClosed(final Project project) {
  }
}
