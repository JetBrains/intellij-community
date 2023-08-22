// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.jetbrains.python.debugger.pydev.ExceptionBreakpointCommandFactory;
import org.jetbrains.annotations.Nullable;

public abstract class ExceptionBreakpointProperties<T> extends XBreakpointProperties<T> implements ExceptionBreakpointCommandFactory{
  @Attribute("exception")
  public String myException;

  /** Python exception class name. */
  @NlsSafe
  public String getException() {
    return myException;
  }

  public abstract String getExceptionBreakpointId();

  public void setCondition(@Nullable String condition) {
  }

  public void setLogExpression(@Nullable String condition) {
  }
}
