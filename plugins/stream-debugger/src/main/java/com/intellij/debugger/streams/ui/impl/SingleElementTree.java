/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.ui.impl;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class SingleElementTree extends CollectionTree {
  SingleElementTree(@NotNull Value value,
                    @NotNull List<TraceElement> traceElements,
                    @NotNull EvaluationContextImpl evaluationContext) {
    super(Collections.singletonList(value), traceElements, evaluationContext);
    addTreeListener(new XDebuggerTreeListener() {
      @Override
      public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
        final TreePath path = node.getPath();
        if (path.getPathCount() == 2) {
          ApplicationManager.getApplication().invokeLater(() -> expandPath(path));
          removeTreeListener(this);
        }
      }
    });
  }
}
