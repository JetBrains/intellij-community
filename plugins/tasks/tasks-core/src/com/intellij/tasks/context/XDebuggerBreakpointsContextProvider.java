/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.tasks.context;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 7/12/13
 */
public class XDebuggerBreakpointsContextProvider extends WorkingContextProvider {

  private final XBreakpointManagerImpl myBreakpointManager;

  public XDebuggerBreakpointsContextProvider(XDebuggerManager xDebuggerManager) {
    myBreakpointManager = (XBreakpointManagerImpl)xDebuggerManager.getBreakpointManager();
  }

  @NotNull
  @Override
  public String getId() {
    return "xDebugger";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "XDebugger breakpoints";
  }

  @Override
  public void saveContext(Element toElement) throws WriteExternalException {
    XBreakpointManagerImpl.BreakpointManagerState state = myBreakpointManager.getState();
    Element serialize = XmlSerializer.serialize(state, new SerializationFilter() {
      @Override
      public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
        return accessor.read(bean) != null;
      }
    });
    toElement.addContent(serialize.removeContent());
  }

  @Override
  public void loadContext(Element fromElement) throws InvalidDataException {
    XBreakpointManagerImpl.BreakpointManagerState state =
      XmlSerializer.deserialize(fromElement, XBreakpointManagerImpl.BreakpointManagerState.class);
    myBreakpointManager.loadState(state);
  }

  @Override
  public void clearContext() {
    XBreakpointBase<?,?,?>[] breakpoints = myBreakpointManager.getAllBreakpoints();
    for (final XBreakpointBase<?, ?, ?> breakpoint : breakpoints) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          myBreakpointManager.removeBreakpoint(breakpoint);
        }
      });
    }
  }
}
