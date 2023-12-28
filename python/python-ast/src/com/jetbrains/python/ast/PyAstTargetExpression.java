// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.ast.docstring.DocStringUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstTargetExpression extends PyAstQualifiedExpression, PsiNamedElement, PsiNameIdentifierOwner, PyAstDocStringOwner,
                                               PyAstQualifiedNameOwner, PyAstReferenceOwner,
                                               PyAstPossibleClassMember, PyAstTypeCommentOwner, PyAstAnnotationOwner {
  PyAstTargetExpression[] EMPTY_ARRAY = new PyAstTargetExpression[0];

  @Override
  @Nullable
  default String getName() {
    ASTNode node = getNameElement();
    return node != null ? node.getText() : null;
  }

  @Override
  default int getTextOffset() {
    final ASTNode nameElement = getNameElement();
    return nameElement != null ? nameElement.getStartOffset() : getTextRange().getStartOffset();
  }

  @Override
  @Nullable
  default ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  default PsiElement getNameIdentifier() {
    final ASTNode nameElement = getNameElement();
    return nameElement == null ? null : nameElement.getPsi();
  }

  @Override
  default String getReferencedName() {
    return getName();
  }

  @Override
  default PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  default PyAstAnnotation getAnnotation() {
    PsiElement topTarget = this;
    while (topTarget.getParent() instanceof PyAstParenthesizedExpression) {
      topTarget = topTarget.getParent();
    }
    final PsiElement parent = topTarget.getParent();
    if (parent != null) {
      final PyAstAssignmentStatement assignment = ObjectUtils.tryCast(parent, PyAstAssignmentStatement.class);
      if (assignment != null) {
        final PyAstExpression[] targets = assignment.getRawTargets();
        if (targets.length == 1 && targets[0] == topTarget) {
          return assignment.getAnnotation();
        }
      }
      else if (parent instanceof PyAstTypeDeclarationStatement) {
        return ((PyAstTypeDeclarationStatement)parent).getAnnotation();
      }
    }
    return null;
  }

  @Nullable
  @Override
  default PyAstExpression getQualifier() {
    ASTNode qualifier = getNode().findChildByType(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
    return qualifier != null ? (PyAstExpression)qualifier.getPsi() : null;
  }

  @Override
  default boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  @Nullable
  default PyAstClass getContainingClass() {
    final PsiElement parent = PsiTreeUtil.getParentOfType(this, PyAstFunction.class, PyAstClass.class);
    if (parent instanceof PyAstClass) {
      return (PyAstClass)parent;
    }
    if (parent instanceof PyAstFunction) {
      return ((PyAstFunction)parent).getContainingClass();
    }
    return null;
  }

  @Nullable
  @Override
  default PyAstStringLiteralExpression getDocStringExpression() {
    final PsiElement parent = getParent();
    if (parent instanceof PyAstAssignmentStatement || parent instanceof PyAstTypeDeclarationStatement) {
      final PsiElement nextSibling = PyPsiUtilsCore.getNextNonCommentSibling(parent, true);
      if (nextSibling instanceof PyAstExpressionStatement) {
        final PyAstExpression expression = ((PyAstExpressionStatement)nextSibling).getExpression();
        if (expression instanceof PyAstStringLiteralExpression) {
          return (PyAstStringLiteralExpression)expression;
        }
      }
    }
    return null;
  }

  /**
   * Find the value that maps to this target expression in an enclosing assignment expression.
   * Does not work with other expressions (e.g. if the target is in a 'for' loop).
   *
   * Operates at the AST level.
   *
   * @return the expression assigned to target via an enclosing assignment expression, or null.
   */
  @Nullable
  default PyAstExpression findAssignedValue() {
    PyPsiUtilsCore.assertValid(this);
    return CachedValuesManager.getCachedValue(this,
                                              () -> CachedValueProvider.Result
                                                .create(findAssignedValueInternal(), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private PyAstExpression findAssignedValueInternal() {
    final PyAstAssignmentStatement assignment = PsiTreeUtil.getParentOfType(this, PyAstAssignmentStatement.class);
    if (assignment != null) {
      var mapping = assignment.getTargetsToValuesMapping();
      for (final var pair : mapping) {
        PyAstExpression assigned_to = pair.getFirst();
        if (assigned_to == this) {
          return pair.getSecond();
        }
      }
    }
    final PyAstImportElement importElement = PsiTreeUtil.getParentOfType(this, PyAstImportElement.class);
    if (importElement != null) {
      return importElement.getImportReferenceExpression();
    }
    final PyAstAssignmentExpression assignmentExpression = ObjectUtils.tryCast(getParent(), PyAstAssignmentExpression.class);
    if (assignmentExpression != null) {
      return assignmentExpression.getAssignedValue();
    }
    return null;
  }

  /**
   * Returns the qualified name (if there is any) assigned to the expression.
   *
   * This method does not access AST if underlying PSI is stub based.
   */
  @Nullable
  default QualifiedName getAssignedQName() {
    final PyAstExpression value = findAssignedValue();
    return value instanceof PyAstReferenceExpression ? ((PyAstReferenceExpression)value).asQualifiedName() : null;
  }

  /**
   * If the value assigned to the target expression is a call, returns the (unqualified and unresolved) name of the
   * callee. Otherwise, returns null.
   *
   * @return the name of the callee or null if the assigned value is not a call.
   */
  @Nullable
  default QualifiedName getCalleeName() {
    final PyAstExpression value = findAssignedValue();
    return value instanceof PyAstCallExpression ? PyPsiUtilsCore.asQualifiedName(((PyAstCallExpression)value).getCallee()) : null;
  }

  /**
   * Checks if target has assigned value.
   *
   * This method does not access AST if underlying PSI is stub based.
   *
   * @return true if target has assigned expression, false otherwise (e.g. in type declaration statement).
   */
  default boolean hasAssignedValue() {
    return true;
  }

  @Override
  @Nullable
  default String getDocStringValue() {
    return DocStringUtilCore.getDocStringValue(this);
  }
}
