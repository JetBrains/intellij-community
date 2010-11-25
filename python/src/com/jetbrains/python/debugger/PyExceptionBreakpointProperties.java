package com.jetbrains.python.debugger;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyExceptionBreakpointProperties extends XBreakpointProperties<PyExceptionBreakpointProperties> {
  @Attribute("exception")
  public String myException;
  @Attribute("notifyAlways")
  public boolean myNotifyAlways;
  @Attribute("notifyOnTerminate")
  public boolean myNotifyOnTerminate;


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
    myNotifyAlways = state.myNotifyAlways;
    myNotifyOnTerminate = state.myNotifyOnTerminate;
  }

  public String getException() {
    return myException;
  }

  public boolean isNotifyOnTerminate() {
    return myNotifyOnTerminate;
  }

  public void setNotifyOnTerminate(boolean notifyOnTerminate) {
    myNotifyOnTerminate = notifyOnTerminate;
  }

  public boolean isNotifyAlways() {
    return myNotifyAlways;
  }

  public void setNotifyAlways(boolean notifyAlways) {
    myNotifyAlways = notifyAlways;
  }
}
