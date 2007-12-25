package com.intellij.xdebugger;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerUtil {
  public static XDebuggerUtil getInstance() {
    return ServiceManager.getService(XDebuggerUtil.class);
  }

  public abstract XLineBreakpointType<?>[] getLineBreakpointTypes();

  public abstract void toggleLineBreakpoint(@NotNull Project project, @NotNull VirtualFile file, int line);
  public abstract <P extends XBreakpointProperties> void toggleLineBreakpoint(@NotNull Project project, @NotNull XLineBreakpointType<P> type,
                                                                              @NotNull VirtualFile file, int line);

  public abstract void removeBreakpoint(Project project, XBreakpoint<?> breakpoint);
}
