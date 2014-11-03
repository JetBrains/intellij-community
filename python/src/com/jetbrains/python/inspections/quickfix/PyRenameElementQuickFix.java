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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ktisha
 */
public class PyRenameElementQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return "Rename element";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Rename element";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element instanceof PyReferenceExpression) {
      final PsiReference reference = element.getReference();
      if (reference == null) return;
      element = reference.resolve();
    }
    final PsiNameIdentifierOwner nameOwner = element instanceof PsiNameIdentifierOwner ?
                                             (PsiNameIdentifierOwner)element :
                                             PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner.class, true);
    if (nameOwner != null) {
      final VirtualFile virtualFile = nameOwner.getContainingFile().getVirtualFile();
      if (virtualFile != null) {
        final Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), true);
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          renameInUnitTestMode(project, nameOwner, editor);
        }
        else {
          if (checkLocalScope(element) != null && (nameOwner instanceof PyNamedParameter || nameOwner instanceof PyTargetExpression)) {
            new VariableInplaceRenamer(nameOwner, editor).performInplaceRename();
          }
          else {
            PsiElementRenameHandler.invoke(nameOwner, project, ScopeUtil.getScopeOwner(nameOwner), editor);
          }
        }
      }
    }
  }

  @Nullable
  protected PsiElement checkLocalScope(PsiElement element) {
    final SearchScope searchScope = PsiSearchHelper.SERVICE.getInstance(element.getProject()).getUseScope(element);
    if (searchScope instanceof LocalSearchScope) {
      final PsiElement[] elements = ((LocalSearchScope)searchScope).getScope();
      return PsiTreeUtil.findCommonParent(elements);
    }

    return null;
  }

  private static void renameInUnitTestMode(@NotNull Project project, @NotNull PsiNameIdentifierOwner nameOwner,
                                           @Nullable Editor editor) {
    final PsiElement substitution = RenamePsiElementProcessor.forElement(nameOwner).substituteElementToRename(nameOwner, editor);
    if (substitution != null) {
      new RenameProcessor(project, substitution, "a", false, false).run();
    }
  }
}
