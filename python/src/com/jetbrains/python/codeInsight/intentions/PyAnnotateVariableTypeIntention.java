// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil.Pep484IncompatibleTypeException;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return PyBundle.message("INTN.add.type.hint.for.variable.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile) || file instanceof PyDocstringFile) {
      return false;
    }
    final List<PyTargetExpression> resolved = findSuitableTargetsUnderCaret(project, editor, file);
    if (resolved.size() != 1) {
      return false;
    }

    setText(PyBundle.message("INTN.add.type.hint.for.variable", resolved.get(0).getName()));
    return true;
  }

  @NotNull
  private static List<PyTargetExpression> findSuitableTargetsUnderCaret(@NotNull Project project, Editor editor, PsiFile file) {
    final int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    final PyReferenceOwner elementAtCaret = PsiTreeUtil.getParentOfType(file.findElementAt(offset),
                                                                        PyReferenceExpression.class, PyTargetExpression.class);
    if (elementAtCaret == null) {
      return Collections.emptyList();
    }

    final ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(project, file);
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(typeEvalContext);
    // TODO filter out targets defined in stubs
    return StreamEx.of(resolveReferenceAugAssignmentsAware(elementAtCaret, resolveContext))
      .select(PyTargetExpression.class)
      .filter(target -> !index.isInLibraryClasses(target.getContainingFile().getVirtualFile()))
      .filter(target -> canBeAnnotated(target))
      .filter(target -> !isAnnotated(target, typeEvalContext))
      .toList();
  }

  @NotNull
  private static StreamEx<PsiElement> resolveReferenceAugAssignmentsAware(@NotNull PyReferenceOwner element,
                                                                          @NotNull PyResolveContext resolveContext) {
    return StreamEx.of(PyUtil.multiResolveTopPriority(element, resolveContext))
                   .filter(resolved -> resolved instanceof PyTargetExpression || resolved != element)
                   .flatMap(resolved -> expandResolveAugAssignments(resolved, resolveContext))
                   .distinct();
  }

  @NotNull
  private static StreamEx<PsiElement> expandResolveAugAssignments(@NotNull PsiElement element, @NotNull PyResolveContext context) {
    if (element instanceof PyReferenceExpression && PyAugAssignmentStatementNavigator.getStatementByTarget(element) != null) {
      return StreamEx.of(resolveReferenceAugAssignmentsAware((PyReferenceOwner)element, context));
    }
    return StreamEx.of(element);
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
    else if (isInstanceAttribute(target, context)) {
      // Set isDefinition=true to start searching right from the class level.
      //noinspection ConstantConditions
      final List<PyTargetExpression> classLevelDefinitions = findClassLevelDefinitions(target, context);
      return ContainerUtil.exists(classLevelDefinitions, PyAnnotateVariableTypeIntention::hasInlineAnnotation);
    }
    return false;
  }

  @NotNull
  private static List<PyTargetExpression> findClassLevelDefinitions(@NotNull PyTargetExpression target, @NotNull TypeEvalContext context) {
    assert target.getContainingClass() != null;
    assert target.getName() != null;
    final PyClassTypeImpl classType = new PyClassTypeImpl(target.getContainingClass(), true);
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
    final List<? extends RatedResolveResult> classAttrs =
      classType.resolveMember(target.getName(), target, AccessDirection.READ, resolveContext, true);
    if (classAttrs == null) {
      return Collections.emptyList();
    }
    return StreamEx.of(classAttrs)
      .map(RatedResolveResult::getElement)
      .select(PyTargetExpression.class)
      .filter(x -> ScopeUtil.getScopeOwner(x) instanceof PyClass)
      .toList();
  }

  private static boolean isInstanceAttribute(@NotNull PyTargetExpression target, @NotNull TypeEvalContext context) {
    final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
    if (target.isQualified() && target.getContainingClass() != null && scopeOwner instanceof PyFunction) {

      if (context.maySwitchToAST(target)) {
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
        //noinspection ConstantConditions
        return StreamEx.of(PyUtil.multiResolveTopPriority(target.getQualifier(), resolveContext))
          .select(PyParameter.class)
          .filter(PyParameter::isSelf)
          .anyMatch(p -> PsiTreeUtil.getParentOfType(p, PyFunction.class) == scopeOwner);
      }
      else {
        return PyUtil.isInstanceAttribute(target);
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
    try {
      if (preferSyntacticAnnotation(annotationTarget)) {
        insertVariableAnnotation(annotationTarget);
      }
      else {
        insertVariableTypeComment(annotationTarget);
      }
    }
    catch (Pep484IncompatibleTypeException e) {
      HintManager.getInstance().showErrorHint(editor, e.getMessage());
    }
  }

  private static boolean preferSyntacticAnnotation(@NotNull PyTargetExpression annotationTarget) {
    return LanguageLevel.forElement(annotationTarget).isAtLeast(LanguageLevel.PYTHON36);
  }

  private static void insertVariableAnnotation(@NotNull PyTargetExpression target) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(target.getProject(), target.getContainingFile());
    final PyType inferredType = getInferredTypeOrObject(target, context);
    PyTypeHintGenerationUtil.checkPep484Compatibility(inferredType, context);
    final String annotationText = PythonDocumentationProvider.getTypeHint(inferredType, context);
    final AnnotationInfo info = new AnnotationInfo(annotationText, inferredType);
    if (isInstanceAttribute(target, context)) {
      final List<PyTargetExpression> classLevelAttrs = findClassLevelDefinitions(target, context);
      if (classLevelAttrs.isEmpty()) {
        PyTypeHintGenerationUtil.insertStandaloneAttributeAnnotation(target, context, info, true);
      }
      else {
        PyTypeHintGenerationUtil.insertVariableAnnotation(classLevelAttrs.get(0), context, info, true);
      }
    }
    else {
      PyTypeHintGenerationUtil.insertVariableAnnotation(target, context, info, true);
    }
  }

  private static void insertVariableTypeComment(@NotNull PyTargetExpression target) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(target.getProject(), target.getContainingFile());
    final AnnotationInfo info = generateNestedTypeHint(target, context);
    if (isInstanceAttribute(target, context)) {
      final List<PyTargetExpression> classLevelAttrs = findClassLevelDefinitions(target, context);
      if (classLevelAttrs.isEmpty()) {
        PyTypeHintGenerationUtil.insertStandaloneAttributeTypeComment(target, context, info, true);
      }
      else {
        // Use existing class level definition (say, assignment of the default value) for annotation
        PyTypeHintGenerationUtil.insertVariableTypeComment(classLevelAttrs.get(0), context, info, true);
      }
    }
    else {
      PyTypeHintGenerationUtil.insertVariableTypeComment(target, context, info, true);
    }
  }

  @NotNull
  private static AnnotationInfo generateNestedTypeHint(@NotNull PyTargetExpression target, TypeEvalContext context) {
    final PyElement validTargetParent = PsiTreeUtil.getParentOfType(target, PyForPart.class, PyWithItem.class, PyAssignmentStatement.class);
    assert validTargetParent != null;
    final PsiElement topmostTarget = PsiTreeUtil.findPrevParent(validTargetParent, target);
    final StringBuilder builder = new StringBuilder();
    final List<PyType> types = new ArrayList<>();
    final ArrayList<TextRange> typeRanges = new ArrayList<>();
    generateNestedTypeHint(topmostTarget, context, builder, types, typeRanges);
    return new AnnotationInfo(builder.toString(), types, typeRanges);
  }

  private static void generateNestedTypeHint(@NotNull PsiElement target,
                                             @NotNull TypeEvalContext context,
                                             @NotNull StringBuilder builder,
                                             @NotNull List<PyType> types,
                                             @NotNull List<TextRange> typeRanges) {
    if (target instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)target).getContainedExpression();
      if (contained != null) {
        generateNestedTypeHint(contained, context, builder, types, typeRanges);
      }
    }
    else if (target instanceof PyTupleExpression) {
      builder.append("(");
      final PyExpression[] elements = ((PyTupleExpression)target).getElements();
      for (int i = 0; i < elements.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        generateNestedTypeHint(elements[i], context, builder, types, typeRanges);
      }
      builder.append(")");
    }
    else if (target instanceof PyTypedElement) {
      final PyType singleTargetType = getInferredTypeOrObject((PyTypedElement)target, context);
      PyTypeHintGenerationUtil.checkPep484Compatibility(singleTargetType, context);
      final String singleTargetAnnotation = PythonDocumentationProvider.getTypeHint(singleTargetType, context);
      types.add(singleTargetType);
      typeRanges.add(TextRange.from(builder.length(), singleTargetAnnotation.length()));
      builder.append(singleTargetAnnotation);
    }
  }

  @Nullable
  private static PyType getInferredTypeOrObject(@NotNull PyTypedElement target, @NotNull TypeEvalContext context) {
    final PyType inferred = context.getType(target);
    return inferred != null ? inferred : PyBuiltinCache.getInstance(target).getObjectType();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
