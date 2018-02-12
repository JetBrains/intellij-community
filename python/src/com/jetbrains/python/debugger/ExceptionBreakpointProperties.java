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
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.jetbrains.python.debugger.pydev.ExceptionBreakpointCommandFactory;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public abstract class ExceptionBreakpointProperties<T> extends XBreakpointProperties<T> implements ExceptionBreakpointCommandFactory{
  @Attribute("exception")
  public String myException;

  public String getException() {
    return myException;
  }

  public abstract String getExceptionBreakpointId();

  public void setCondition(@Nullable String condition) {
  }

  public void setLogExpression(@Nullable String condition) {
  }
}
