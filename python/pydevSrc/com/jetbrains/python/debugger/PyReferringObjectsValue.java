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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyReferringObjectsValue extends PyDebugValue {
  private static final Logger LOG = Logger.getInstance(PyReferringObjectsValue.class);

  private final @Nullable PyReferrersLoader myReferrersLoader;

  public PyReferringObjectsValue(@NotNull String name,
                                 String type,
                                 String typeQualifier,
                                 String value,
                                 boolean container,
                                 String shape,
                                 boolean isReturnedVal,
                                 boolean errorOnEval,
                                 @NotNull PyFrameAccessor frameAccessor) {
    super(name, type, typeQualifier, value, container, shape, isReturnedVal, false, errorOnEval, frameAccessor);
    myReferrersLoader = frameAccessor.getReferrersLoader();
  }

  public PyReferringObjectsValue(PyDebugValue debugValue) {
    this(debugValue.getName(), debugValue.getType(), debugValue.getTypeQualifier(), debugValue.getValue(), debugValue.isContainer(),
         debugValue.getShape(), debugValue.isReturnedVal(), debugValue.isErrorOnEval(), debugValue.getFrameAccessor());
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    if (myReferrersLoader == null) {
      LOG.error("Failed to load Referring Objects. Frame accessor: " + getFrameAccessor());
      return;
    }
    myReferrersLoader.loadReferrers(this, new PyDebugCallback<>() {
      @Override
      public void ok(XValueChildrenList value) {
        if (!node.isObsolete()) {
          node.addChildren(value, true);
        }
      }

      @Override
      public void error(PyDebuggerException e) {
        if (!node.isObsolete()) {
          node.setErrorMessage("Unable to display children:" + e.getMessage());
        }
        LOG.warn(e);
      }
    });
  }

  public boolean isField() {
    return false;
  }
}
