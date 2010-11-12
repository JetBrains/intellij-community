package com.jetbrains.python.debugger;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyExceptionBreakpointProperties extends XBreakpointProperties<PyExceptionBreakpointProperties>{
  @Attribute("exception")
  public String myException;

  @SuppressWarnings({"UnusedDeclaration"})
  public PyExceptionBreakpointProperties() {
  }

  public PyExceptionBreakpointProperties(@NotNull final String exception) {
    myException = exception;
  }

  @Override
  public PyExceptionBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(final PyExceptionBreakpointProperties state) {
    myException = state.myException;
  }

  public String getException() {
    return myException;
  }
}
