/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  public void loadState(final PyExceptionBreakpointProperties state) {
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

  public void setCondition(@Nullable String condition) {
    myCondition = condition;
  }

  @Nullable
  public String getLogExpression() {
    return myLogExpression;
  }

  public void setLogExpression(@Nullable String logExpression) {
    myLogExpression = logExpression;
  }

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
