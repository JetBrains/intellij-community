/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyConvertTypeCommentToVariableAnnotation extends PyBaseIntentionAction {
  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiComment comment = findCommentUnderCaret(editor, file);
    if (comment != null) {
      final PyTargetExpression target = findTypeCommentTarget(comment);
      if (target != null) {
        final String annotation = PyTypingTypeProvider.getTypeCommentValue(comment.getText());
        final PsiElement prev = PyPsiUtils.getPrevNonWhitespaceSibling(comment);
        final int commentStart = prev != null ? prev.getTextRange().getEndOffset() : comment.getTextRange().getStartOffset();
        final int commentEnd = comment.getTextRange().getEndOffset();
        final Document document = editor.getDocument();
        runWithDocumentReleasedAndCommitted(project, document, () -> {
          document.deleteString(commentStart, commentEnd);
          document.insertString(target.getTextRange().getEndOffset(), ": " + annotation);
        });
      }
    }
  }

  @NotNull
  @Override
  public String getText() {
    return PyBundle.message("INTN.convert.type.comment.to.variable.annotation.text");
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.type.comment.to.variable.annotation.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (file instanceof PyFile && LanguageLevel.forElement(file).isAtLeast(LanguageLevel.PYTHON36)) {
      final PsiComment comment = findCommentUnderCaret(editor, file);
      return comment != null && isSuitableTypeComment(comment);
    }
    return false;
  }

  @Nullable
  private static PsiComment findCommentUnderCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
  }

  private boolean isSuitableTypeComment(@NotNull PsiComment comment) {
    final String annotation = PyTypingTypeProvider.getTypeCommentValue(comment.getText());
    return annotation != null && findTypeCommentTarget(comment) != null;
  }

  @Nullable
  private PyTargetExpression findTypeCommentTarget(@NotNull PsiComment comment) {
    final PsiElement parent = comment.getParent();
    if (parent instanceof PyAssignmentStatement) {
      final PyAssignmentStatement assignment = (PyAssignmentStatement)parent;
      final PyExpression[] rawTargets = assignment.getRawTargets();
      if (rawTargets.length == 1 && rawTargets[0] instanceof PyTargetExpression) {
        final PyTargetExpression target = (PyTargetExpression)rawTargets[0];
        if (target.getTypeComment() == comment) {
          return target;
        }
      }
    }
    return null;
  }

  public static void runWithDocumentReleasedAndCommitted(@NotNull Project project, @NotNull Document document, @NotNull Runnable runnable) {
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    manager.doPostponedOperationsAndUnblockDocument(document);
    try {
      runnable.run();
    }
    finally {
      manager.commitDocument(document);
    }
  }
}
