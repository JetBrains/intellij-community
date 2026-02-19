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
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * Checks for non-top-level star imports.
 */
public class PyImportAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyImportAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyFromImportStatement(final @NotNull PyFromImportStatement node) {
    if (node.isStarImport() && PsiTreeUtil.getParentOfType(node, PyFunction.class, PyClass.class) != null) {
      myHolder.newAnnotation(HighlightSeverity.WARNING, PyPsiBundle.message("ANN.star.import.at.top.only")).create();
    }
  }
}
