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
import com.intellij.debugger.streams.resolve.ResolvedCall;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vitaliy.Bibaev
 */
public class CollectionView extends JPanel {
  private static final int MAX_STREAM_CALL_LENGTH = 60;
  private CollectionTree myInstancesTree;

  public CollectionView(@NotNull EvaluationContextImpl evaluationContext, @NotNull ResolvedCall call) {
    super(new BorderLayout());
    add(new JBLabel(stringLimit(call.getName() + call.getArguments())), BorderLayout.NORTH);
    final Project project = evaluationContext.getProject();

    myInstancesTree = new CollectionTree(project, call, evaluationContext);

    add(new JBScrollPane(myInstancesTree), BorderLayout.CENTER);
  }

  public CollectionTree getTree() {
    return myInstancesTree;
  }

  private static String stringLimit(@NotNull String str) {
    if (str.length() < MAX_STREAM_CALL_LENGTH) {
      return str;
    }

    return str.substring(0, MAX_STREAM_CALL_LENGTH).trim() + "...";
  }
}
