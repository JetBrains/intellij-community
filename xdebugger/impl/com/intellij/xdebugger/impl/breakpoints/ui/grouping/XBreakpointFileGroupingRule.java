package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public class XBreakpointFileGroupingRule<B extends XLineBreakpoint<?>> extends XBreakpointGroupingRule<B, XBreakpointFileGroup> {
  public XBreakpointFileGroupingRule() {
    super("by-file", XDebuggerBundle.message("rule.name.group.by.file"));
  }

  public XBreakpointFileGroup getGroup(@NotNull final B breakpoint, @NotNull final Collection<XBreakpointFileGroup> groups) {
    XSourcePosition position = breakpoint.getSourcePosition();
    if (position == null) return null;

    VirtualFile file = position.getFile();
    for (XBreakpointFileGroup group : groups) {
      if (group.getFile().equals(file)) {
        return group;
      }
    }

    return new XBreakpointFileGroup(file);
  }
}
