// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.util.xmlb.annotations.Attribute;
import com.jetbrains.python.debugger.pydev.AddExceptionBreakpointCommand;
import com.jetbrains.python.debugger.pydev.ExceptionBreakpointCommand;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyExceptionBreakpointProperties extends ExceptionBreakpointProperties<PyExceptionBreakpointProperties> {
  @Attribute("notifyOnlyOnFirst")
  public boolean myNotifyOnlyOnFirst;
  @Attribute("notifyOnTerminate")
  public boolean myNotifyOnTerminate;
  @Attribute("ignoreLibraries")
  public boolean myIgnoreLibraries;
  public @Nullable String myCondition;
  public @Nullable String myLogExpression;


  @SuppressWarnings({"UnusedDeclaration"})
  public PyExceptionBreakpointProperties() {
  }

  public PyExceptionBreakpointProperties(@NotNull final String exception) {
    myException = exception;
    myNotifyOnTerminate = true;
    myIgnoreLibraries = false;
    myCondition = null;
    myLogExpression = null;
  }

  @Override
  public PyExceptionBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final PyExceptionBreakpointProperties state) {
    myException = state.myException;
    myNotifyOnlyOnFirst = state.myNotifyOnlyOnFirst;
    myNotifyOnTerminate = state.myNotifyOnTerminate;
    myIgnoreLibraries = state.myIgnoreLibraries;
  }

  public boolean isNotifyOnTerminate() {
    return myNotifyOnTerminate;
  }

  public void setNotifyOnTerminate(boolean notifyOnTerminate) {
    myNotifyOnTerminate = notifyOnTerminate;
  }

  public boolean isNotifyOnlyOnFirst() {
    return myNotifyOnlyOnFirst;
  }

  public void setNotifyOnlyOnFirst(boolean notifyOnlyOnFirst) {
    myNotifyOnlyOnFirst = notifyOnlyOnFirst;
  }

  public void setIgnoreLibraries(boolean ignoreLibraries) {
    myIgnoreLibraries = ignoreLibraries;
  }

  public boolean isIgnoreLibraries() {
    return myIgnoreLibraries;
  }

  @Nullable
  public String getCondition() {
    return myCondition;
  }

  @Override
  public void setCondition(@Nullable String condition) {
    myCondition = condition;
  }

  @Nullable
  public String getLogExpression() {
    return myLogExpression;
  }

  @Override
  public void setLogExpression(@Nullable String logExpression) {
    myLogExpression = logExpression;
  }

  @Override
  public String getExceptionBreakpointId() {
    return "python-" + myException;
  }

  @Override
  public ExceptionBreakpointCommand createAddCommand(RemoteDebugger debugger) {
    return ExceptionBreakpointCommand.addExceptionBreakpointCommand(debugger,
                                                                    getExceptionBreakpointId(),
                                                                    getCondition(),
                                                                    getLogExpression(),
                                                                    new AddExceptionBreakpointCommand.ExceptionBreakpointNotifyPolicy(
                                                                      isNotifyOnTerminate(), isNotifyOnlyOnFirst(), isIgnoreLibraries()));
  }

  @Override
  public ExceptionBreakpointCommand createRemoveCommand(RemoteDebugger debugger) {
    return ExceptionBreakpointCommand.removeExceptionBreakpointCommand(debugger, getExceptionBreakpointId());
  }
}
