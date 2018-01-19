// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyAnnotateVariableTypeIntention extends PyBaseIntentionAction {
  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("INTN.annotate.types");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile) || file instanceof PyDocstringFile) {
      return false;
    }
    final List<PyTargetExpression> resolved = findSuitableTargetsUnderCaret(project, editor, file);
    if (resolved.isEmpty() || resolved.size() > 1) {
      return false;
    }

    setText(PyBundle.message("INTN.annotate.types"));
    return true;
  }

  @NotNull
  private static List<PyTargetExpression> findSuitableTargetsUnderCaret(@NotNull Project project, Editor editor, PsiFile file) {
    final PyReferenceOwner elementAtCaret = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()),
                                                                        PyReferenceExpression.class, PyTargetExpression.class);
    if (elementAtCaret == null) {
      return Collections.emptyList();
    }

    final ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(project, file);
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(typeEvalContext);
    // TODO filter out targets defined in stubs
    return StreamEx.of(PyUtil.multiResolveTopPriority(elementAtCaret, resolveContext))
      .select(PyTargetExpression.class)
      .filter(target -> !index.isInLibraryClasses(target.getContainingFile().getVirtualFile()))
      .filter(target -> !isAnnotated(target, typeEvalContext))
      .toList();
  }

  private static boolean isAnnotated(@NotNull PyTargetExpression target, @NotNull TypeEvalContext context) {
    // TODO filter out fields explicitly annotated as Any
    return new PyTypingTypeProvider().getReferenceType(target, context, null) != null;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final List<PyTargetExpression> targets = findSuitableTargetsUnderCaret(project, editor, file);
    assert targets.size() == 1;
    final PyTargetExpression annotationTarget = targets.get(0);
    if (LanguageLevel.forElement(annotationTarget).isAtLeast(LanguageLevel.PYTHON36)) {
      insertVariableAnnotation(annotationTarget);
    }
    else {
      insertVariableTypeComment(annotationTarget);
    }
  }

  private void insertVariableAnnotation(@NotNull PyTargetExpression target) {

  }

  private void insertVariableTypeComment(@NotNull PyTargetExpression target) {

  }
}
