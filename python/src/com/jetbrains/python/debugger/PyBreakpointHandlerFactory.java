package com.jetbrains.python.debugger;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;

/**
 * @author yole
 */
public abstract class PyBreakpointHandlerFactory {
  public static ExtensionPointName<PyBreakpointHandlerFactory> EP_NAME = ExtensionPointName.create("Pythonid.breakpointHandler");

  public abstract XBreakpointHandler createBreakpointHandler(PyDebugProcess process);
}
