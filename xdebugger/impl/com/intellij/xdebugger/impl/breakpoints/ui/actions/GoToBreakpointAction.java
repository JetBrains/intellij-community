package com.intellij.xdebugger.impl.breakpoints.ui.actions;

import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPanelAction;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointsPanel;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
*/
public class GoToBreakpointAction<B extends XBreakpoint<?>> extends XBreakpointPanelAction<B> {
  private final boolean myCloseDialog;

  public GoToBreakpointAction(final @NotNull XBreakpointsPanel<B> panel, final String name, boolean closeDialog) {
    super(panel, name);
    myCloseDialog = closeDialog;
  }

  public boolean isEnabled(@NotNull final Collection<? extends B> breakpoints) {
    if (breakpoints.size() != 1) {
      return false;
    }
    B b = breakpoints.iterator().next();
    Navigatable navigatable = b.getNavigatable();
    return navigatable != null && navigatable.canNavigateToSource();
  }

  public void perform(@NotNull final Collection<? extends B> breakpoints) {
    B b = breakpoints.iterator().next();
    Navigatable navigatable = b.getNavigatable();
    if (navigatable != null) {
      navigatable.navigate(true);
    }
    if (myCloseDialog) {
      myBreakpointsPanel.getParentDialog().close(DialogWrapper.OK_EXIT_CODE);
    }
  }
}
