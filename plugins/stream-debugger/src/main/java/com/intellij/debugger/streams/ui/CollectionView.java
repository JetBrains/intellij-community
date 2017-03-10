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
package com.intellij.debugger.streams.ui;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.trace.smart.TraceElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class CollectionView extends JPanel implements ValuesHighlightingListener, Disposable {
  private static final int MAX_STREAM_CALL_LENGTH = 60;
  private CollectionTree myInstancesTree;

  CollectionView(@NotNull EvaluationContextImpl evaluationContext, @NotNull ResolvedTrace call) {
    super(new BorderLayout());
    add(new JBLabel("stub"), BorderLayout.NORTH);

    myInstancesTree = new CollectionTree(call, evaluationContext);

    add(new JBScrollPane(myInstancesTree), BorderLayout.CENTER);
    Disposer.register(this, myInstancesTree);
  }

  void setBackwardListener(@NotNull ValuesHighlightingListener listener) {
    myInstancesTree.setBackwardListener(listener);
  }

  void setForwardListener(@NotNull ValuesHighlightingListener listener) {
    myInstancesTree.setForwardListener(listener);
  }

  @Override
  public void highlightingChanged(@NotNull List<TraceElement> values, @NotNull PropagationDirection direction) {
    myInstancesTree.highlightingChanged(values, direction);
  }

  @Override
  public void dispose() {

  }

  @NotNull
  private static String stringLimit(@NotNull String str) {
    if (str.length() < MAX_STREAM_CALL_LENGTH) {
      return str;
    }

    return str.substring(0, MAX_STREAM_CALL_LENGTH).trim() + "...";
  }
}
