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

import static com.jetbrains.python.psi.PyUtil.as;

public class PyConvertTypeCommentToVariableAnnotation extends PyBaseIntentionAction {
  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiComment comment = findCommentUnderCaret(editor, file);
    if (comment != null) {
      final String annotation = PyTypingTypeProvider.getTypeCommentValue(comment.getText());
      final PyTargetExpression assignmentTarget = findAssignmentTypeCommentTarget(comment);
      if (assignmentTarget != null) {
        comment.delete();
        final Document document = editor.getDocument();
        runWithDocumentReleasedAndCommitted(project, document, () -> {
          document.insertString(assignmentTarget.getTextRange().getEndOffset(), ": " + annotation);
        });
        return;
      }
      final PyTargetExpression compoundTarget;
      final PyTargetExpression forTarget = findForLoopTypeCommentTarget(comment);
      if (forTarget != null) {
        compoundTarget = forTarget;
      }
      else {
        compoundTarget = findWithStatementTypeCommentTarget(comment);
      }
      if (compoundTarget != null) {
        comment.delete();
        final PyElementGenerator generator = PyElementGenerator.getInstance(project);
        final PyTypeDeclarationStatement declaration = generator.createFromText(LanguageLevel.PYTHON36,
                                                                                PyTypeDeclarationStatement.class,
                                                                                compoundTarget.getText() + ": " + annotation);
        final PyStatement containingStatement = PsiTreeUtil.getParentOfType(compoundTarget, PyStatement.class);
        assert containingStatement != null;
        containingStatement.getParent().addBefore(declaration, containingStatement);
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

  private static boolean isSuitableTypeComment(@NotNull PsiComment comment) {
    final String annotation = PyTypingTypeProvider.getTypeCommentValue(comment.getText());
    return annotation != null && (findAssignmentTypeCommentTarget(comment) != null ||
                                  findForLoopTypeCommentTarget(comment) != null ||
                                  findWithStatementTypeCommentTarget(comment) != null);
  }

  @Nullable
  private static PyTargetExpression findAssignmentTypeCommentTarget(@NotNull PsiComment comment) {
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

  @Nullable
  private static PyTargetExpression findForLoopTypeCommentTarget(@NotNull PsiComment comment) {
    final PsiElement parent = comment.getParent();
    if (parent instanceof PyForPart) {
      final PyForPart forPart = (PyForPart)parent;
      final PyTargetExpression target = as(forPart.getTarget(), PyTargetExpression.class);
      if (target != null && target.getTypeComment() == comment) {
        return target;
      }
    }
    return null;
  }

  @Nullable
  private static PyTargetExpression findWithStatementTypeCommentTarget(@NotNull PsiComment comment) {
    final PsiElement parent = comment.getParent();
    if (parent instanceof PyWithStatement) {
      final PyWithStatement withStatement = (PyWithStatement)parent;
      final PyWithItem[] withItems = withStatement.getWithItems();
      if (withItems.length == 1) {
        final PyTargetExpression target = as(withItems[0].getTarget(), PyTargetExpression.class);
        if (target != null && target.getTypeComment() == comment) {
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
