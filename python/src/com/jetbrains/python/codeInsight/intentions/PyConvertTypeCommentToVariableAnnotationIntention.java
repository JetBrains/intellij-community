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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class PyConvertTypeCommentToVariableAnnotationIntention extends PyBaseIntentionAction {
  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiComment typeComment = findCommentUnderCaret(editor, file);
    if (typeComment != null) {
      final SmartPointerManager manager = SmartPointerManager.getInstance(project);
      final SmartPsiElementPointer<PsiComment> commentPointer = manager.createSmartPsiElementPointer(typeComment);
      final Map<PyTargetExpression, String> map = mapTargetsToAnnotations(typeComment);
      if (!map.isEmpty()) {
        if (typeComment.getParent() instanceof PyAssignmentStatement && map.size() == 1) {
          final PyTargetExpression target = ContainerUtil.getFirstItem(map.keySet());
          assert target != null;
          PyTypeHintGenerationUtil.insertVariableAnnotation(target, null, new AnnotationInfo(map.get(target)), false);
        }
        else {
          for (Map.Entry<PyTargetExpression, String> entry : new ArrayList<>(map.entrySet())) {
            PyTypeHintGenerationUtil.insertVariableAnnotation(entry.getKey(), null, new AnnotationInfo(entry.getValue()), false);
          }
        }

        final PsiComment staleComment = commentPointer.getElement();
        if (staleComment != null) {
          staleComment.delete();
        }
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
    return annotation != null && !mapTargetsToAnnotations(comment).isEmpty();
  }

  @NotNull
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
    return Collections.emptyMap();
  }

  @NotNull
  private static Map<PyTargetExpression, String> mapTargetsToAnnotations(@NotNull PyExpression targetExpr, @NotNull PsiComment typeComment) {
    final PyTargetExpression firstTarget = PsiTreeUtil.findChildOfType(targetExpr, PyTargetExpression.class, false);
    if (firstTarget == null || firstTarget.getTypeComment() != typeComment) {
      return Collections.emptyMap();
    }

    final String annotation = PyTypingTypeProvider.getTypeCommentValue(typeComment.getText());
    if (annotation != null) {
      if (targetExpr instanceof PyTargetExpression) {
        return ImmutableMap.of((PyTargetExpression)targetExpr, annotation);
      }

      final PyElementGenerator generator = PyElementGenerator.getInstance(targetExpr.getProject());
      final PyExpression parsed;
      try {
        parsed = generator.createExpressionFromText(LanguageLevel.PYTHON36, annotation);
      }
      catch (IncorrectOperationException e) {
        return Collections.emptyMap();
      }
      final Map<PyTargetExpression, PyExpression> targetToExpr = PyTypingTypeProvider.mapTargetsToAnnotations(targetExpr, parsed);
      return EntryStream.of(targetToExpr).mapValues(PyExpression::getText).toCustomMap(LinkedHashMap::new);
    }
    return Collections.emptyMap();
  }
}
