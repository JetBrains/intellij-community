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
package com.jetbrains.python.codeInsight.fstrings;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Implementation of {@link PyRecursiveElementVisitor} that recursively visits Python files injected into f-strings.
 * You can check whether you are inside injected fragment using {@link #myContainingFString} field.
 */
public class PyFStringAwareRecursiveVisitor extends PyRecursiveElementVisitor {
  protected PyStringLiteralExpression myContainingFString;

  @Override
  public void visitPyStringLiteralExpression(PyStringLiteralExpression pyString) {
    final List<TextRange> formatNodeRanges = ContainerUtil.mapNotNull(pyString.getStringNodes(), node -> {
      final PyUtil.StringNodeInfo nodeInfo = new PyUtil.StringNodeInfo(node);
      return nodeInfo.isFormatted() ? nodeInfo.getAbsoluteContentRange().shiftRight(-pyString.getTextOffset()) : null;
    });
    if (!formatNodeRanges.isEmpty()) {
      final InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(pyString.getProject());
      for (Pair<PsiElement, TextRange> pair : ContainerUtil.notNullize(injectionManager.getInjectedPsiFiles(pyString))) {
        final PyFile pyFile = as(pair.getFirst(), PyFile.class);
        if (pyFile != null && ContainerUtil.exists(formatNodeRanges, range -> range.contains(pair.getSecond()))) {
          myContainingFString = pyString;
          try {
            pyFile.accept(this);
          }
          finally {
            myContainingFString = null;
          }
        }
      }
    }
    super.visitPyStringLiteralExpression(pyString);
  }
}
