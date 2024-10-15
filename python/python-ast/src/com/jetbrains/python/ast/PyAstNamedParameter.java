/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.ParamHelperCore;
import com.jetbrains.python.ast.impl.PyUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a named parameter, as opposed to a tuple parameter.
 */
@ApiStatus.Experimental
public interface PyAstNamedParameter extends PyAstParameter, PsiNamedElement, PsiNameIdentifierOwner, PyAstExpression, PyAstTypeCommentOwner,
                                             PyAstAnnotationOwner {
  @Nullable
  default ASTNode getNameIdentifierNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  @Nullable
  default String getName() {
    ASTNode node = getNameIdentifierNode();
    return node != null ? node.getText() : null;
  }

  @Override
  default int getTextOffset() {
    ASTNode node = getNameIdentifierNode();
    return node == null ? getNode().getStartOffset() : node.getTextRange().getStartOffset();
  }

  @Override
  @Nullable
  default PsiElement getNameIdentifier() {
    final ASTNode node = getNameIdentifierNode();
    return node == null ? null : node.getPsi();
  }

  default boolean isPositionalContainer() {
    return getNode().findChildByType(PyTokenTypes.MULT) != null;
  }

  default boolean isKeywordContainer() {
    return getNode().findChildByType(PyTokenTypes.EXP) != null;
  }

  @Override
  @Nullable
  default PyAstExpression getDefaultValue() {
    final ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
    if (nodes.length > 0) {
      return (PyAstExpression)nodes[0].getPsi();
    }
    return null;
  }

  @Override
  default boolean hasDefaultValue() {
    return getDefaultValue() != null;
  }

  @Override
  @Nullable
  default String getDefaultValueText() {
    return ParamHelperCore.getDefaultValueText(getDefaultValue());
  }

  /**
   * @param includeDefaultValue if true, include the default value after an "=".
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param, and name.
   */
  @NotNull
  default String getRepr(boolean includeDefaultValue) {
    final StringBuilder sb = new StringBuilder();

    sb.append(ParamHelperCore.getNameInSignature(this));

    if (includeDefaultValue) {
      sb.append(ObjectUtils.notNull(ParamHelperCore.getDefaultValuePartInSignature(getDefaultValueText(), false), ""));
    }

    return sb.toString();
  }

  @Override
  @NotNull
  default PyAstNamedParameter getAsNamed() {
    return this;
  }

  @Override
  @Nullable
  default PyAstTupleParameter getAsTuple() {
    return null; // we're not a tuple
  }

  @Nullable
  @Override
  PyAstAnnotation getAnnotation();
  /**
   * Parameter is considered "keyword-only" if it appears after named or unnamed positional vararg parameter.
   * See PEP-3102 for more details.
   *
   * @return whether this parameter is keyword-only
   */
  default boolean isKeywordOnly() {
    final PyAstParameterList parameters = getStubOrPsiParentOfType(PyAstParameterList.class);
    if (parameters == null) {
      return false;
    }
    boolean varargSeen = false;
    for (PyAstParameter param : parameters.getParameters()) {
      if (param == this) {
        break;
      }
      final PyAstNamedParameter named = param.getAsNamed();
      if ((named != null && named.isPositionalContainer()) || param instanceof PyAstSingleStarParameter) {
        varargSeen = true;
        break;
      }
    }
    return varargSeen;
  }

  @Override
  default boolean isSelf() {
    if (isPositionalContainer() || isKeywordContainer()) {
      return false;
    }
    PyAstFunction function = getStubOrPsiParentOfType(PyAstFunction.class);
    if (function == null) {
      return false;
    }
    final PyAstClass cls = function.getContainingClass();
    final PyAstParameter[] parameters = function.getParameterList().getParameters();
    if (cls != null && parameters.length > 0 && parameters[0] == this) {
      if (PyUtilCore.isNewMethod(function)) {
        return true;
      }
      final PyAstFunction.Modifier modifier = function.getModifier();
      if (modifier != PyAstFunction.Modifier.STATICMETHOD) {
        return true;
      }
    }
    return false;
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyNamedParameter(this);
  }

  @Nullable
  @Override
  default PsiComment getTypeComment() {
    for (PsiElement next = getNextSibling(); next != null; next = next.getNextSibling()) {
      if (next.textContains('\n')) break;
      if (!(next instanceof PsiWhiteSpace)) {
        if (",".equals(next.getText())) continue;
        if (next instanceof PsiComment && PyUtilCore.getTypeCommentValue(next.getText()) != null) {
          return (PsiComment)next;
        }
        break;
      }
    }
    return null;
  }
}
