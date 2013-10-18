package com.jetbrains.python.debugger;

import com.intellij.util.xmlb.annotations.Attribute;
import com.jetbrains.python.debugger.pydev.AddExceptionBreakpointCommand;
import com.jetbrains.python.debugger.pydev.ExceptionBreakpointCommand;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyExceptionBreakpointProperties extends ExceptionBreakpointProperties<PyExceptionBreakpointProperties> {
  @Attribute("notifyAlways")
  public boolean myNotifyAlways;
  @Attribute("notifyOnlyOnFirst")
  public boolean myNotifyOnlyOnFirst;
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
    myNotifyOnlyOnFirst = state.myNotifyOnlyOnFirst;
    myNotifyOnTerminate = state.myNotifyOnTerminate;
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

  public boolean isNotifyOnlyOnFirst() {
    return myNotifyOnlyOnFirst;
  }

  public void setNotifyOnlyOnFirst(boolean notifyOnlyOnFirst) {
    myNotifyOnlyOnFirst = notifyOnlyOnFirst;
  }

  @Override
  public ExceptionBreakpointCommand createAddCommand(RemoteDebugger debugger) {
    return ExceptionBreakpointCommand.addExceptionBreakpointCommand(debugger, getException(),
                                                                    new AddExceptionBreakpointCommand.ExceptionBreakpointNotifyPolicy(
                                                                      isNotifyAlways(),
                                                                      isNotifyOnTerminate(), isNotifyOnlyOnFirst()));
  }

  @Override
  public ExceptionBreakpointCommand createRemoveCommand(RemoteDebugger debugger) {
    return ExceptionBreakpointCommand.removeExceptionBreakpointCommand(debugger, getException());
  }
}
