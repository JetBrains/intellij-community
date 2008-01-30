package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.ArrayList;

/**
 * @author nik
 */
public class XBreakpointPanelProvider extends BreakpointPanelProvider<XBreakpoint> {

  public int getPriority() {
    return 0;
  }

  @Nullable
  public XBreakpoint<?> findBreakpoint(@NotNull final Project project, @NotNull final Document document, final int offset) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    int line = document.getLineNumber(offset);
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    for (XLineBreakpointType<?> type : XDebuggerUtil.getInstance().getLineBreakpointTypes()) {
      XLineBreakpoint<? extends XBreakpointProperties> breakpoint = breakpointManager.findBreakpointAtLine(type, file, line);
      if (breakpoint != null) {
        return breakpoint;
      }
    }

    return null;
  }

  @NotNull
  public Collection<AbstractBreakpointPanel<XBreakpoint>> getBreakpointPanels(@NotNull final Project project, @NotNull final DialogWrapper parentDialog) {
    XBreakpointType<?,?>[] types = XBreakpointType.getBreakpointTypes();
    ArrayList<AbstractBreakpointPanel<XBreakpoint>> panels = new ArrayList<AbstractBreakpointPanel<XBreakpoint>>();
    for (XBreakpointType<? extends XBreakpoint<?>, ?> type : types) {
      XBreakpointsPanel<?> panel = createBreakpointsPanel(project, parentDialog, type);
      panels.add(panel);
    }
    return panels;
  }

  private static <B extends XBreakpoint<?>> XBreakpointsPanel<B> createBreakpointsPanel(final Project project, DialogWrapper parentDialog, final XBreakpointType<B, ?> type) {
    return new XBreakpointsPanel<B>(project, parentDialog, type);
  }

  public void onDialogClosed(final Project project) {
  }
}
