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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyConvertLocalFunctionToTopLevelFunction extends BaseIntentionAction {
  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.local.function.to.top.level.function");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PyFunction nestedFunction = findNestedFunctionUnderCaret(editor, file);
    return nestedFunction != null;
  }

  @Nullable
  private static PyFunction findNestedFunctionUnderCaret(Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) return null;
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    if (element == null) {
      return null;
    }
    if (isLocalFunction(element.getParent()) && ((PyFunction)element.getParent()).getNameIdentifier() == element) {
      return (PyFunction)element.getParent();
    }
    final PsiReference reference = element.getReference();
    if (reference == null) {
      return null;
    }
    final PsiElement resolved = reference.resolve();
    if (isLocalFunction(resolved)) {
      return (PyFunction)resolved;
    }
    return null;
  }

  private static boolean isLocalFunction(@Nullable PsiElement resolved) {
    if (resolved instanceof PyFunction && PsiTreeUtil.getParentOfType(resolved, ScopeOwner.class, true) instanceof PyFunction) {
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PyFunction function = findNestedFunctionUnderCaret(editor, file);
    assert function != null;
    final ControlFlow flow = ControlFlowCache.getControlFlow(function);
  }
}
