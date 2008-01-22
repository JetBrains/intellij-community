package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XToggleLineBreakpointActionHandler extends DebuggerActionHandler {

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    XSourcePosition position = XDebuggerUtilImpl.getCaretPosition(project, event.getDataContext());
    if (position == null) return false;

    XLineBreakpointType<?>[] breakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    for (XLineBreakpointType<?> breakpointType : breakpointTypes) {
      if (breakpointType.canPutAt(position.getFile(), position.getLine())) {
        return true;
      }
    }
    return false;
  }

  public void perform(@NotNull final Project project, final AnActionEvent event) {
    XSourcePosition position = XDebuggerUtilImpl.getCaretPosition(project, event.getDataContext());
    if (position == null) return;

    int line = position.getLine();
    VirtualFile file = position.getFile();
    for (XLineBreakpointType<?> type : XDebuggerUtil.getInstance().getLineBreakpointTypes()) {
      if (type.canPutAt(file, line)) {
        XDebuggerUtil.getInstance().toggleLineBreakpoint(project, type, file, line);
        return;
      }
    }
  }

}
