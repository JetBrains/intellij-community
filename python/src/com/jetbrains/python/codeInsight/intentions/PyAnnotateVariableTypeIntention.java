// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
      .filter(target -> canBeAnnotated(target))
      .filter(target -> !isAnnotated(target, typeEvalContext))
      .toList();
  }

  private static boolean canBeAnnotated(@NotNull PyTargetExpression target) {
    final PsiElement directParent = target.getParent();
    if (directParent instanceof PyImportElement ||
        directParent instanceof PyComprehensionForComponent ||
        directParent instanceof PyGlobalStatement ||
        directParent instanceof PyNonlocalStatement) {
      return false;
    }
    return PsiTreeUtil.getParentOfType(target, PyWithItem.class, PyAssignmentStatement.class, PyForPart.class) != null;
  }

  // TODO unify this logic with PyTypingTypeProvider somehow
  private static boolean isAnnotated(@NotNull PyTargetExpression target, @NotNull TypeEvalContext context) {
    final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
    final String name = target.getName();
    if (scopeOwner == null || name == null) {
      return false;
    }

    if (!target.isQualified()) {
      if (hasInlineAnnotation(target)) {
        return true;
      }

      StreamEx<PyTargetExpression> candidates = null;
      if (context.maySwitchToAST(target)) {
        final Scope scope = ControlFlowCache.getScope(scopeOwner);
        candidates = StreamEx.of(scope.getNamedElements(name, false)).select(PyTargetExpression.class);
      }
      // Unqualified target expression in either class or module
      else if (scopeOwner instanceof PyFile) {
        candidates = StreamEx.of(((PyFile)scopeOwner).getTopLevelAttributes()).filter(t -> name.equals(t.getName()));
      }
      else if (scopeOwner instanceof PyClass) {
        candidates = StreamEx.of(((PyClass)scopeOwner).getClassAttributes()).filter(t -> name.equals(t.getName()));
      }
      if (candidates != null) {
        return candidates.anyMatch(PyAnnotateVariableTypeIntention::hasInlineAnnotation);
      }
    }
    else {
      final PyClass pyClass = target.getContainingClass();
      if (pyClass != null && scopeOwner instanceof PyFunction) {
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);

        final boolean isInstanceAttribute;
        if (context.maySwitchToAST(target)) {
          //noinspection ConstantConditions
          isInstanceAttribute = StreamEx.of(PyUtil.multiResolveTopPriority(target.getQualifier(), resolveContext))
            .select(PyParameter.class)
            .filter(PyParameter::isSelf)
            .anyMatch(p -> PsiTreeUtil.getParentOfType(p, PyFunction.class) == scopeOwner);
        }
        else {
          isInstanceAttribute = PyUtil.isInstanceAttribute(target);
        }
        if (!isInstanceAttribute) {
          return false;
        }
        // Set isDefinition=true to start searching right from the class level.
        final PyClassTypeImpl classType = new PyClassTypeImpl(pyClass, true);
        final List<? extends RatedResolveResult> classAttrs =
          classType.resolveMember(name, target, AccessDirection.READ, resolveContext, true);
        if (classAttrs == null) {
          return false;
        }
        return StreamEx.of(classAttrs)
          .map(RatedResolveResult::getElement)
          .select(PyTargetExpression.class)
          .filter(x -> ScopeUtil.getScopeOwner(x) instanceof PyClass)
          .anyMatch(PyAnnotateVariableTypeIntention::hasInlineAnnotation);
      }
    }
    return false;
  }

  private static boolean hasInlineAnnotation(@NotNull PyTargetExpression target) {
    return target.getAnnotationValue() != null || target.getTypeCommentAnnotation() != null;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final List<PyTargetExpression> targets = findSuitableTargetsUnderCaret(project, editor, file);
    assert targets.size() == 1;
    final PyTargetExpression annotationTarget = targets.get(0);
    if (preferSyntacticAnnotation(annotationTarget)) {
      insertVariableAnnotation(annotationTarget);
    }
    else {
      insertVariableTypeComment(annotationTarget);
    }
  }

  private static boolean preferSyntacticAnnotation(@NotNull PyTargetExpression annotationTarget) {
    return LanguageLevel.forElement(annotationTarget).isAtLeast(LanguageLevel.PYTHON36);
  }

  private static void insertVariableAnnotation(@NotNull PyTargetExpression target) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(target.getProject(), target.getContainingFile());
    final PyType inferredType = context.getType(target);
    final String annotationText = PythonDocumentationProvider.getTypeName(inferredType, context);
    PyTypeHintGenerationUtil.insertVariableAnnotation(target, annotationText, true);
  }

  private static void insertVariableTypeComment(@NotNull PyTargetExpression target) {
    final Pair<String, List<TextRange>> annotationAndRanges = generateNestedTypeHint(target);
    PyTypeHintGenerationUtil.insertVariableTypeComment(target, annotationAndRanges.getFirst(), true, annotationAndRanges.getSecond());
  }

  @NotNull
  private static Pair<String, List<TextRange>> generateNestedTypeHint(@NotNull PyTargetExpression target) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(target.getProject(), target.getContainingFile());
    final PyElement validTargetParent = PsiTreeUtil.getParentOfType(target, PyForPart.class, PyWithItem.class, PyAssignmentStatement.class);
    assert validTargetParent != null;
    final PsiElement topmostTarget = PsiTreeUtil.findPrevParent(validTargetParent, target);
    final StringBuilder builder = new StringBuilder();
    final ArrayList<TextRange> typeRanges = new ArrayList<>();
    generateNestedTypeHint(topmostTarget, context, builder, typeRanges);
    return Pair.create(builder.toString(), typeRanges);
  }

  private static void generateNestedTypeHint(@NotNull PsiElement target,
                                             @NotNull TypeEvalContext context,
                                             @NotNull StringBuilder builder,
                                             @NotNull List<TextRange> typeRanges) {
    if (target instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)target).getContainedExpression();
      if (contained != null) {
        generateNestedTypeHint(contained, context, builder, typeRanges);
      }
    }
    else if (target instanceof PyTupleExpression) {
      builder.append("(");
      final PyExpression[] elements = ((PyTupleExpression)target).getElements();
      for (int i = 0; i < elements.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        generateNestedTypeHint(elements[i], context, builder, typeRanges);
      }
      builder.append(")");
    }
    else if (target instanceof PyTypedElement) {
      final String type = PythonDocumentationProvider.getTypeName(context.getType((PyTypedElement)target), context);
      typeRanges.add(TextRange.from(builder.length(), type.length()));
      builder.append(type);
    }
  }
}
