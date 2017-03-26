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

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PyConvertTypeCommentToVariableAnnotationIntention extends PyBaseIntentionAction {
  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiComment typeComment = findCommentUnderCaret(editor, file);
    if (typeComment != null) {
      final Map<PyTargetExpression, String> map = mapTargetsToAnnotations(typeComment);
      if (map != null) {
        if (typeComment.getParent() instanceof PyAssignmentStatement && map.size() == 1) {
          final Document document = editor.getDocument();
          runWithDocumentReleasedAndCommitted(project, document, () -> {
            final PyTargetExpression target = ContainerUtil.getFirstItem(map.keySet());
            assert target != null;
            document.insertString(target.getTextRange().getEndOffset(), ": " + map.get(target));
          });
        }
        else {
          final PyStatement statement = PsiTreeUtil.getParentOfType(typeComment, PyStatement.class);
          assert statement != null;

          final PyElementGenerator generator = PyElementGenerator.getInstance(project);
          final List<Map.Entry<PyTargetExpression, String>> entries = new ArrayList<>(map.entrySet());
          Collections.reverse(entries);

          PsiElement anchor = statement;
          for (Map.Entry<PyTargetExpression, String> entry : entries) {
            final PyTargetExpression target = entry.getKey();
            final String annotation = entry.getValue();
            final PyTypeDeclarationStatement declaration = generator.createFromText(LanguageLevel.PYTHON36,
                                                                                    PyTypeDeclarationStatement.class,
                                                                                    target.getText() + ": " + annotation);
            anchor = statement.getParent().addBefore(declaration, anchor);
          }
        }

        PyPsiUtils.assertValid(typeComment);
        typeComment.delete();
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
    return annotation != null && mapTargetsToAnnotations(comment) != null;
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

  @Nullable
  private static Map<PyTargetExpression, String> mapTargetsToAnnotations(@NotNull PsiComment typeComment) {
    final PsiElement parent = typeComment.getParent();
    if (parent instanceof PyAssignmentStatement) {
      final PyAssignmentStatement assignment = (PyAssignmentStatement)parent;
      final PyExpression[] rawTargets = assignment.getRawTargets();
      if (rawTargets.length == 1) {
        return mapTargetsToAnnotations(rawTargets[0], typeComment);
      }
    }
    else if (parent instanceof PyForPart) {
      final PyForPart forPart = (PyForPart)parent;
      final PyExpression target = forPart.getTarget();
      if (target != null) {
        return mapTargetsToAnnotations(target, typeComment);
      }
    }
    else if (parent instanceof PyWithStatement) {
      final PyWithItem[] withItems = ((PyWithStatement)parent).getWithItems();
      if (withItems.length == 1) {
        final PyExpression target = withItems[0].getTarget();
        if (target != null) {
          return mapTargetsToAnnotations(target, typeComment);
        }
      }
    }
    return null;
  }

  @Nullable
  private static Map<PyTargetExpression, String> mapTargetsToAnnotations(@NotNull PyExpression targetExpr, @NotNull PsiComment typeComment) {
    final PyTargetExpression firstTarget = PsiTreeUtil.findChildOfType(targetExpr, PyTargetExpression.class, false);
    if (firstTarget == null || firstTarget.getTypeComment() != typeComment) {
      return null;
    }

    final String annotation = PyTypingTypeProvider.getTypeCommentValue(typeComment.getText());
    if (annotation != null) {
      if (targetExpr instanceof PyTargetExpression) {
        return ImmutableMap.of((PyTargetExpression)targetExpr, annotation);
      }

      final PyElementGenerator generator = PyElementGenerator.getInstance(targetExpr.getProject());
      final PyExpression parsed = generator.createExpressionFromText(LanguageLevel.PYTHON36, annotation);
      if (parsed != null) {
        return mapTargetsToAnnotations(targetExpr, parsed);
      }
    }
    return null;
  }

  @Nullable
  private static Map<PyTargetExpression, String> mapTargetsToAnnotations(@NotNull PyExpression targetExpr,
                                                                         @NotNull PyExpression typeExpr) {
    final PyExpression targetsNoParen = PyPsiUtils.flattenParens(targetExpr);
    final PyExpression typesNoParen = PyPsiUtils.flattenParens(typeExpr);
    if (targetsNoParen == null || typesNoParen == null) {
      return null;
    }
    if (targetsNoParen instanceof PySequenceExpression && typesNoParen instanceof PySequenceExpression) {
      final Ref<Map<PyTargetExpression, String>> result = new Ref<>(new LinkedHashMap<>());
      mapTargetsToExpressions((PySequenceExpression)targetsNoParen, (PySequenceExpression)typesNoParen, result);
      return result.get();
    }
    else if (targetsNoParen instanceof PyTargetExpression && !(typesNoParen instanceof PySequenceExpression)) {
      return ImmutableMap.of((PyTargetExpression)targetsNoParen, typesNoParen.getText());
    }
    return null;
  }

  private static void mapTargetsToExpressions(@NotNull PySequenceExpression targetSequence,
                                              @NotNull PySequenceExpression valueSequence,
                                              @NotNull Ref<Map<PyTargetExpression, String>> result) {
    final PyExpression[] targets = targetSequence.getElements();
    final PyExpression[] values = valueSequence.getElements();

    if (targets.length != values.length) {
      result.set(null);
      return;
    }

    for (int i = 0; i < targets.length; i++) {
      final PyExpression target = PyPsiUtils.flattenParens(targets[i]);
      final PyExpression value = PyPsiUtils.flattenParens(values[i]);

      if (target == null || value == null) {
        result.set(null);
        return;
      }

      if (target instanceof PySequenceExpression && value instanceof PySequenceExpression) {
        mapTargetsToExpressions((PySequenceExpression)target, (PySequenceExpression)value, result);
        if (result.isNull()) {
          return;
        }
      }
      else if (target instanceof PyTargetExpression && !(value instanceof PySequenceExpression)) {
        final Map<PyTargetExpression, String> map = result.get();
        assert map != null;
        map.put((PyTargetExpression)target, value.getText());
      }
      else {
        result.set(null);
        return;
      }
    }
  }
}
