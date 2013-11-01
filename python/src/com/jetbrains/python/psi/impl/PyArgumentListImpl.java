/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class PyArgumentListImpl extends PyElementImpl implements PyArgumentList {
  public PyArgumentListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyArgumentList(this);
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

  public void addArgument(PyExpression arg) {
    // it should find the comma after the argument to add after, and add after
    // that. otherwise it won't deal with comments nicely
    if (arg instanceof PyKeywordArgument) {
      PyKeywordArgument keywordArgument = (PyKeywordArgument)arg;
      PyKeywordArgument lastKeyArg = null;
      PyExpression firstNonKeyArg = null;
      for (PsiElement element : getChildren()) {
        if (element instanceof PyKeywordArgument) {
          lastKeyArg = (PyKeywordArgument)element;
        }
        else if (element instanceof PyExpression && firstNonKeyArg == null) {
          firstNonKeyArg = (PyExpression)element;
        }
      }
      if (lastKeyArg != null) {
        // add after last key arg
        addArgumentNode(keywordArgument, lastKeyArg.getNode().getTreeNext(), true);

      }
      else if (firstNonKeyArg != null) {
        // add before first non key arg
        addArgumentNode(keywordArgument, firstNonKeyArg.getNode(), true);

      }
      else {
        // add as only argument
        addArgumentLastWithoutComma(arg);
      }
    }
    else {
      final PyExpression[] args = getArguments();
      if (args.length > 0) {
        addArgumentAfter(arg, args [args.length-1]);
      }
      else {
        addArgumentLastWithoutComma(arg);
      }
    }
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
      ASTNode before = PyUtil.getNextNonWhitespace(pars[0]);
      ASTNode anchorBefore;
      if (before != null && elementPrecedesElementsOfType(before, PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens())) {
        ASTNode comma = PyElementGenerator.getInstance(getProject()).createComma();
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
    return children.length == 0 ? null : children[children.length-1];
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
        ASTNode next = PyUtil.getNextNonWhitespace(node);
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
    //noinspection SuspiciousMethodCalls
    if (Arrays.asList(getArguments()).contains(node.getPsi())) {
      ASTNode next = PyPsiUtils.getNextComma(node);
      if (next == null) {
        next = PyPsiUtils.getPrevComma(node);
      }
      if (next != null) {
        deleteChildInternal(next);
      }
    }
    super.deleteChildInternal(node);
  }

  @NotNull
  public CallArgumentsMapping analyzeCall(PyResolveContext resolveContext) {
    return analyzeCall(resolveContext, 0);
  }

  @NotNull
  public CallArgumentsMapping analyzeCall(PyResolveContext resolveContext, int offset) {
    final CallArgumentsMappingImpl ret = new CallArgumentsMappingImpl(this);
    // declaration-based checks
    // proper arglist is: [positional,...][name=value,...][*tuple,][**dict]
    // where "positional" may be a tuple of nested "positional" parameters, too.
    // following the spec: http://docs.python.org/ref/calls.html
    PyCallExpression call = getCallExpression();
    if (call != null) {
      PyCallExpression.PyMarkedCallee resolvedCallee = call.resolveCallee(resolveContext, offset);
      if (resolvedCallee != null) {
        ret.mapArguments(resolvedCallee, resolveContext.getTypeEvalContext());
      }
      else {
        ret.verifyArguments();
      }
    }
    return ret;
  }
}
