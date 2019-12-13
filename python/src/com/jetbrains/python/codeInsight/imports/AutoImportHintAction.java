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
package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author yole
 */
public class AutoImportHintAction implements LocalQuickFix, HintAction, HighPriorityAction {
  private final AutoImportQuickFix myDelegate;

  public AutoImportHintAction(AutoImportQuickFix delegate) {
    myDelegate = delegate;
  }

  @Override
  public boolean showHint(@NotNull Editor editor) {
    List<? extends ImportCandidateHolder> imports = myDelegate.getCandidates();
    if (!PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP ||
        HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true) ||
        imports.isEmpty()) {
      return false;
    }
    final PsiElement element = myDelegate.getStartElement();
    PyPsiUtils.assertValid(element);
    if (element == null || !element.isValid()) {
      return false;
    }
    final PyElement pyElement = as(element, PyElement.class);
    String initialName = myDelegate.getNameToImport();
    if (pyElement == null || !initialName.equals(pyElement.getName())) {
      return false;
    }
    final PsiReference reference = myDelegate.findOriginalReference(element);
    if (reference == null || AutoImportQuickFix.isResolved(reference)) {
      return false;
    }
    if (element instanceof PyQualifiedExpression && ((PyQualifiedExpression)element).isQualified()) {
      return false; // we cannot be qualified
    }

    final String message = ShowAutoImportPass.getMessage(
      imports.size() > 1,
      ImportCandidateHolder.getQualifiedName(initialName, imports.get(0).getPath(), imports.get(0).getImportElement())
    );
    final ImportFromExistingAction action = myDelegate.createAction(element);
    HintManager.getInstance().showQuestionHint(
      editor, message,
      element.getTextOffset(),
      element.getTextRange().getEndOffset(), action);
    return true;
  }

  @NotNull
  @Override
  public String getText() {
    return myDelegate.getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myDelegate.isAvailable();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myDelegate.invoke(file);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  public String getName() {
    return myDelegate.getName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myDelegate.getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    myDelegate.applyFix(project, descriptor);
  }
}
