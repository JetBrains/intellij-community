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
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Annotates misplaced 'break' and 'continue'.
 */
public class PyBreakContinueAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyBreakContinueAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyBreakStatement(final @NotNull PyBreakStatement node) {
    if (node.getLoopStatement() == null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.break.outside.loop")).create();
    }
  }

  @Override
  public void visitPyContinueStatement(final @NotNull PyContinueStatement node) {
    if (node.getLoopStatement() == null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.continue.outside.loop")).create();
    }
  }
}
