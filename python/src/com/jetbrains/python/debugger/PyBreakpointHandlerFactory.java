// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;


public abstract class PyBreakpointHandlerFactory {
  public static final ExtensionPointName<PyBreakpointHandlerFactory> EP_NAME = ExtensionPointName.create("Pythonid.breakpointHandler");

  public abstract XBreakpointHandler createBreakpointHandler(PyDebugProcess process);
}
