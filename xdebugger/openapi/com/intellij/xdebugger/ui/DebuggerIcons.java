package com.intellij.xdebugger.ui;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author nik
 */
public interface DebuggerIcons {

  Icon ENABLED_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_set_breakpoint.png");
  Icon DISABLED_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_disabled_breakpoint.png");

}
