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
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Collections2;
import com.google.common.collect.Queues;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PyArgumentListImpl extends PyElementImpl implements PyArgumentList {

  // Filters all expressions but keyword arguments
  private static final NoKeyArguments NO_KEY_ARGUMENTS = new NoKeyArguments();

  public PyArgumentListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyArgumentList(this);
  }

  @Override
  @NotNull
  public Collection<PyExpression> getArgumentExpressions() {
    final PyExpression[] arguments = getArguments();
    final Collection<PyExpression> result = new ArrayList<>(arguments.length);
    for (final PyExpression expression : arguments) {
      if (expression instanceof PyKeywordArgument) {
        final PyExpression valueExpression = ((PyKeywordArgument)expression).getValueExpression();
        result.add(valueExpression);
      }
      if (expression instanceof PyReferenceExpression) {
        result.add(expression);
      }
    }
    return result;
  }

  @NotNull
  public PyExpression[] getArguments() {
    return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), PyExpression.EMPTY_ARRAY);
  }

  @Nullable
  public PyKeywordArgument getKeywordArgument(String name) {
    ASTNode node = getNode().getFirstChildNode();
    while (node != null) {
      if (node.getElementType() == PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION) {
        PyKeywordArgument arg = (PyKeywordArgument)node.getPsi();
        String keyword = arg.getKeyword();
        if (keyword != null && keyword.equals(name)) return arg;
      }
      node = node.getTreeNext();
    }
    return null;
  }

  @Override
  public void addArgument(@NotNull final PyExpression arg) {
    final PyElementGenerator generator = new PyElementGeneratorImpl(getProject());

    // Adds param to appropriate place
    final Deque<PyKeywordArgument> keywordArguments = getKeyWordArguments();
    final Deque<PyExpression> parameters = getParameters();

    if (keywordArguments.isEmpty() && parameters.isEmpty()) {
      generator.insertItemIntoListRemoveRedundantCommas(this, null, arg);
      return;
    }


    if (arg instanceof PyKeywordArgument) {
      if (parameters.isEmpty()) {
        generator.insertItemIntoListRemoveRedundantCommas(this, keywordArguments.getLast(), arg);
      }
      else {
        if (keywordArguments.isEmpty()) {
          generator.insertItemIntoListRemoveRedundantCommas(this, parameters.getLast(), arg);
        }
        else {
          generator.insertItemIntoListRemoveRedundantCommas(this, keywordArguments.getLast(), arg);
        }
      }
    }
    else {
      if (parameters.isEmpty()) {
        generator.insertItemIntoListRemoveRedundantCommas(this, null, arg);
      }
      else {
        generator.insertItemIntoListRemoveRedundantCommas(this, parameters.getLast(), arg);
      }
    }
  }


  /**
   * @return parameters (as opposite to keyword arguments)
   */
  @NotNull
  private Deque<PyExpression> getParameters() {
    final PyExpression[] childrenOfType = PsiTreeUtil.getChildrenOfType(this, PyExpression.class);
    if (childrenOfType == null) {
      return new ArrayDeque<>(0);
    }
    return Queues.newArrayDeque(Collections2.filter(Arrays.asList(childrenOfType), NO_KEY_ARGUMENTS));
  }

  /**
   * @return keyword arguments (as opposite to parameters)
   */
  @NotNull
  private Deque<PyKeywordArgument> getKeyWordArguments() {
    return Queues.newArrayDeque(PsiTreeUtil.findChildrenOfType(this, PyKeywordArgument.class));
  }

  public void addArgumentFirst(PyExpression arg) {
    ASTNode node = getNode();
    ASTNode[] pars = node.getChildren(TokenSet.create(PyTokenTypes.LPAR));
    if (pars.length == 0) {
      // there's no starting paren
      try {
        add(arg);
      }
      catch (IncorrectOperationException e1) {
        throw new IllegalStateException(e1);
      }
    }
    else {
      ASTNode before = PyPsiUtils.getNextNonWhitespaceSibling(pars[0]);
      ASTNode anchorBefore;
      if (before != null && elementPrecedesElementsOfType(before, PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens())) {
        ASTNode comma = createComma();
        node.addChild(comma, before);
        node.addChild(ASTFactory.whitespace(" "), before);
        anchorBefore = comma;
      }
      else {
        anchorBefore = before;
      }
      ASTNode argNode = arg.getNode();
      if (anchorBefore == null) {
        node.addChild(argNode);
      }
      else {
        node.addChild(argNode, anchorBefore);
      }
    }
  }

  /**
   * @return newly created comma
   */
  @NotNull
  private ASTNode createComma() {
    return PyElementGenerator.getInstance(getProject()).createComma();
  }

  private static boolean elementPrecedesElementsOfType(ASTNode before, TokenSet expressions) {
    ASTNode node = before;
    while (node != null) {
      if (expressions.contains(node.getElementType())) return true;
      node = node.getTreeNext();
    }
    return false;
  }

  private void addArgumentLastWithoutComma(PyExpression arg) {
    ASTNode par = getClosingParen();
    if (par == null) {
      // there's no ending paren
      try {
        add(arg);
      }
      catch (IncorrectOperationException e1) {
        throw new IllegalStateException(e1);
      }
    }
    else {
      getNode().addChild(arg.getNode(), par);
    }
  }

  @Nullable
  public ASTNode getClosingParen() {
    ASTNode node = getNode();
    final ASTNode[] children = node.getChildren(TokenSet.create(PyTokenTypes.RPAR));
    return children.length == 0 ? null : children[children.length - 1];
  }

  private void addArgumentNode(PyExpression arg, ASTNode beforeThis, boolean commaFirst) {
    ASTNode comma = PyElementGenerator.getInstance(getProject()).createComma();
    ASTNode node = getNode();
    ASTNode argNode = arg.getNode();
    if (commaFirst) {
      node.addChild(comma, beforeThis);
      node.addChild(ASTFactory.whitespace(" "), beforeThis);
      node.addChild(argNode, beforeThis);
    }
    else {
      node.addChild(argNode, beforeThis);
      node.addChild(comma, beforeThis);
      node.addChild(ASTFactory.whitespace(" "), beforeThis);
    }
  }

  public void addArgumentAfter(PyExpression argument, @Nullable PyExpression afterThis) {
    if (afterThis == null) {
      addArgumentFirst(argument);
      return;
    }
    boolean good = false;
    for (PyExpression expression : getArguments()) {
      if (expression == afterThis) {
        good = true;
        break;
      }
    }
    if (!good) {
      throw new IllegalArgumentException("Expression " + afterThis + " is not an argument (" + Arrays.toString(getArguments()) + ")");
    }
    // CASES:
    ASTNode node = afterThis.getNode().getTreeNext();
    while (node != null) {
      IElementType type = node.getElementType();
      if (type == PyTokenTypes.RPAR) {
        // 1: Nothing, just add
        addArgumentNode(argument, node, true);
        break;
      }
      else if (PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens().contains(type)) {
        // 2: After some argument followed by comma: after comma, add element, add comma
        // 3: After some argument not followed by comma: add comma, add element
        addArgumentNode(argument, node, true);
        break;
      }
      else if (type == PyTokenTypes.COMMA) {
        ASTNode next = PyPsiUtils.getNextNonWhitespaceSibling(node);
        if (next == null) {
          addArgumentLastWithoutComma(argument);
        }
        else if (next.getElementType() == PyTokenTypes.RPAR) {
          addArgumentNode(argument, next, false);
        }
        else {
          addArgumentNode(argument, next, false);
        }
        break;
      }
      node = node.getTreeNext();
    }
  }

  @Nullable
  public PyCallExpression getCallExpression() {
    return PsiTreeUtil.getParentOfType(this, PyCallExpression.class);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode node) {
    if (ArrayUtil.contains(node.getPsi(), getArguments())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, node.getPsi());
    }
    super.deleteChildInternal(node);
  }

  private static class NoKeyArguments extends NotNullPredicate<PyExpression> {
    @Override
    protected boolean applyNotNull(@NotNull final PyExpression input) {
      return (PsiTreeUtil.getParentOfType(input, PyKeywordArgument.class) == null) && !(input instanceof PyKeywordArgument);
    }
  }

  @Nullable
  @Override
  public PyExpression getValueExpressionForParam(@NotNull final FunctionParameter parameter) {
    final String parameterName = parameter.getName();
    if (parameterName != null) {
      final PyKeywordArgument kwarg = getKeywordArgument(parameterName);
      if (kwarg != null) {
        return kwarg.getValueExpression();
      }
    }

    final PyExpression[] arguments = getArguments();
    final int position = parameter.getPosition();
    if ((position != FunctionParameter.POSITION_NOT_SUPPORTED) && (arguments.length > position)) {
      final PyExpression result = arguments[position];
      if (result instanceof PyKeywordArgument) {
        ((PyKeywordArgument)result).getValueExpression();
      }
      else {
        return result;
      }
    }

    return null;
  }
}
