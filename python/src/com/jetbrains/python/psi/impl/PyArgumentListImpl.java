/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
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
  @PsiCached
  public PyExpression[] getArguments() {
    return childrenToPsi(PyElementTypes.EXPRESSIONS, PyExpression.EMPTY_ARRAY);
  }

  @Nullable
  @PsiCached
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
    PyUtil.ensureWritable(this);
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
      addArgumentLastWithoutComma(arg);
    }
  }

  public void addArgumentFirst(PyExpression arg) {
    PyUtil.ensureWritable(this);
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
      if (before != null && elementPrecedesElementsOfType(before, PyElementTypes.EXPRESSIONS)) {
        ASTNode comma = getLanguage().getElementGenerator().createComma(getProject());
        node.addChild(comma, before);
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
    PyUtil.ensureWritable(this);
    ASTNode node = getNode();
    ASTNode[] pars = node.getChildren(TokenSet.create(PyTokenTypes.RPAR));
    if (pars.length == 0) {
      // there's no ending paren
      try {
        add(arg);
      }
      catch (IncorrectOperationException e1) {
        throw new IllegalStateException(e1);
      }

    }
    else {
      node.addChild(arg.getNode(), pars[pars.length - 1]);
    }
  }

  private void addArgumentNode(PyExpression arg, ASTNode beforeThis, boolean commaFirst) {
    PyUtil.ensureWritable(this);
    ASTNode comma = getLanguage().getElementGenerator().createComma(getProject());
    ASTNode node = getNode();
    ASTNode argNode = arg.getNode();
    if (commaFirst) {
      node.addChild(comma, beforeThis);
      node.addChild(argNode, beforeThis);
    }
    else {
      node.addChild(argNode, beforeThis);
      node.addChild(comma, beforeThis);
    }
  }

  public void addArgumentAfter(PyExpression argument, @Nullable PyExpression afterThis) {
    PyUtil.ensureWritable(this);
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
      throw new IllegalArgumentException("Expression " + afterThis + " is not an argument (" + getArguments() + ")");
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
      else if (PyElementTypes.EXPRESSIONS.contains(type)) {
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

  @SuppressWarnings({"SuspiciousMethodCalls"})
  protected void deletePyChild(PyBaseElementImpl element) throws IncorrectOperationException {
    if (Arrays.asList(getArguments()).contains(element)) {
      ASTNode node = element.getNode();
      ASTNode next = PyPsiUtils.getNextComma(node);
      if (next == null) {
        next = PyPsiUtils.getPrevComma(node);
      }
      ASTNode me = getNode();
      if (next != null) {
        me.removeChild(next);
      }
      me.removeChild(node);
    }
  }

}
