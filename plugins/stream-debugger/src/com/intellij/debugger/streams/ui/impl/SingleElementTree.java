// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class SingleElementTree extends CollectionTree {
  SingleElementTree(@Nullable Value value,
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
