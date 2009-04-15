package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.codeInsight.folding.impl.ExpandRegionHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XToggleLineBreakpointActionHandler extends DebuggerActionHandler {

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    XSourcePosition position = XDebuggerUtilImpl.getCaretPosition(project, event.getDataContext());
    if (position == null) return false;

    XLineBreakpointType<?>[] breakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XLineBreakpointType<?> breakpointType : breakpointTypes) {
      final VirtualFile file = position.getFile();
      final int line = position.getLine();
      if (breakpointType.canPutAt(file, line, project) || breakpointManager.findBreakpointAtLine(breakpointType, file, line) != null) {
        return true;
      }
    }
    return false;
  }

  public void perform(@NotNull final Project project, final AnActionEvent event) {
    XSourcePosition position = XDebuggerUtilImpl.getCaretPosition(project, event.getDataContext());
    if (position == null) return;

    ExpandRegionHandler.expandRegionAtCaret(project, event.getData(PlatformDataKeys.EDITOR));

    int line = position.getLine();
    VirtualFile file = position.getFile();
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XLineBreakpointType<?> type : XDebuggerUtil.getInstance().getLineBreakpointTypes()) {
      if (type.canPutAt(file, line, project) || breakpointManager.findBreakpointAtLine(type, file, line) != null) {
        XDebuggerUtil.getInstance().toggleLineBreakpoint(project, type, file, line);
        return;
      }
    }
  }

}
