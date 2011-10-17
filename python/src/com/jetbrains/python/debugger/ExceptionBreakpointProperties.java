package com.jetbrains.python.debugger;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.jetbrains.python.debugger.pydev.ExceptionBreakpointCommandFactory;

/**
 * @author traff
 */
public abstract class ExceptionBreakpointProperties<T> extends XBreakpointProperties<T> implements ExceptionBreakpointCommandFactory{
  @Attribute("exception")
  public String myException;

  public String getException() {
    return myException;
  }

}
