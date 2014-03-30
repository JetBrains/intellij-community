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
package com.jetbrains.python.validation;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.*;
import com.jetbrains.python.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User : catherine
 */
public abstract class CompatibilityVisitor extends PyAnnotator {
  protected List<LanguageLevel> myVersionsToProcess;
  private String myCommonMessage = "Python version ";

  private static final Map<LanguageLevel, Set<String>> AVAILABLE_PREFIXES = Maps.newHashMap();

  static {
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON24, Sets.newHashSet("R", "U", "UR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON25, Sets.newHashSet("R", "U", "UR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON26, Sets.newHashSet("R", "U", "UR", "B", "BR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON27, Sets.newHashSet("R", "U", "UR", "B", "BR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON30, Sets.newHashSet("R", "B"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON31, Sets.newHashSet("R", "B", "BR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON32, Sets.newHashSet("R", "B", "BR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON33, Sets.newHashSet("R", "U", "B", "BR", "RB"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON34, Sets.newHashSet("R", "U", "B", "BR", "RB"));
  }

  public CompatibilityVisitor(List<LanguageLevel> versionsToProcess) {
    myVersionsToProcess = versionsToProcess;
  }

  @Override
  public void visitPyDictCompExpression(PyDictCompExpression node) {
    super.visitPyDictCompExpression(node);
    int len = 0;
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      if (!languageLevel.supportsSetLiterals()) {
        len = appendLanguageLevel(message, len, languageLevel);
      }
    }
    commonRegisterProblem(message, " not support dictionary comprehensions", len, node, new ConvertDictCompQuickFix(), false);
  }

  @Override
  public void visitPySetLiteralExpression(PySetLiteralExpression node) {
    super.visitPySetLiteralExpression(node);
    int len = 0;
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      if (!languageLevel.supportsSetLiterals()) {
        len = appendLanguageLevel(message, len, languageLevel);
      }
    }
    commonRegisterProblem(message, " not support set literal expressions", len, node, new ConvertSetLiteralQuickFix(), false);
  }

  @Override
  public void visitPySetCompExpression(PySetCompExpression node) {
    super.visitPySetCompExpression(node);
    int len = 0;
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      if (!languageLevel.supportsSetLiterals()) {
        len = appendLanguageLevel(message, len, languageLevel);
      }
    }
    commonRegisterProblem(message, " not support set comprehensions", len, node, null, false);
  }

  @Override
  public void visitPyExceptBlock(PyExceptPart node) {
    super.visitPyExceptBlock(node);
    PyExpression exceptClass = node.getExceptClass();
    if (exceptClass != null) {
      if (myVersionsToProcess.contains(LanguageLevel.PYTHON24) || myVersionsToProcess.contains(LanguageLevel.PYTHON25)) {
        PsiElement element = exceptClass.getNextSibling();
        while (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
        }
        if (element != null && "as".equals(element.getText())) {
          registerProblem(node, myCommonMessage + "2.4, 2.5 do not support this syntax.");
        }
      }

      int len = 0;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (languageLevel.isPy3K()) {
          PsiElement element = exceptClass.getNextSibling();
          while (element instanceof PsiWhiteSpace) {
            element = element.getNextSibling();
          }
          if (element != null && ",".equals(element.getText())) {
            len = appendLanguageLevel(message, len, languageLevel);
          }
        }
      }
      commonRegisterProblem(message, " not support this syntax.", len, node, new ReplaceExceptPartQuickFix());
    }
  }

  @Override
  public void visitPyImportStatement(PyImportStatement node) {
    super.visitPyImportStatement(node);
    PyIfStatement ifParent = PsiTreeUtil.getParentOfType(node, PyIfStatement.class);
    if (ifParent != null)
      return;
    PyImportElement[] importElements = node.getImportElements();
    int len = 0;
    String moduleName = "";
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      for (PyImportElement importElement : importElements) {
        final QualifiedName qName = importElement.getImportedQName();
        if (qName != null) {
          if (!languageLevel.isPy3K()) {
            if (qName.matches("builtins")) {
              len = appendLanguageLevel(message, len, languageLevel);
              moduleName = "builtins";
            }
          }
          else {
            if (qName.matches("__builtin__")) {
              len = appendLanguageLevel(message, len, languageLevel);
              moduleName = "__builtin__";
            }
          }
        }
      }
    }
    commonRegisterProblem(message, " not have module " + moduleName, len, node, new ReplaceBuiltinsQuickFix());
  }

  @Override
  public void visitPyStarExpression(PyStarExpression node) {
    super.visitPyStarExpression(node);
    int len = 0;
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      if (!languageLevel.isPy3K()) {
        len = appendLanguageLevel(message, len, languageLevel);
      }
    }
    commonRegisterProblem(message, " not support this syntax. Starred expressions are not allowed as assignment targets in Python 2",
                          len, node, null);
  }

  @Override
  public void visitPyBinaryExpression(PyBinaryExpression node) {
    super.visitPyBinaryExpression(node);
    int len = 0;
    if (node.isOperator("<>")) {
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (languageLevel.isPy3K()) {
          len = appendLanguageLevel(message, len, languageLevel);
        }
      }
      commonRegisterProblem(message, " not support <>, use != instead.", len, node, new ReplaceNotEqOperatorQuickFix());
    }
  }

  @Override
  public void visitPyNumericLiteralExpression(final PyNumericLiteralExpression node) {
    super.visitPyNumericLiteralExpression(node);
    int len = 0;
    LocalQuickFix quickFix = null;
    StringBuilder message = new StringBuilder(myCommonMessage);
    String suffix = "";
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      if (languageLevel.isPy3K()) {
        if (!node.isIntegerLiteral()) {
          continue;
        }
        final String text = node.getText();
        if (text.endsWith("l") || text.endsWith("L")) {
          len = appendLanguageLevel(message, len, languageLevel);
          suffix = " not support a trailing \'l\' or \'L\'.";
          quickFix = new RemoveTrailingLQuickFix();
        }
        if (text.length() > 1 && text.charAt(0) == '0') {
          final char c = Character.toLowerCase(text.charAt(1));
          if (c != 'o' && c != 'b' && c != 'x') {
            boolean isNull = true;
            for (char a : text.toCharArray()) {
              if ( a != '0') {
                isNull = false;
                break;
              }
            }
            if (!isNull) {
              len = appendLanguageLevel(message, len, languageLevel);
              quickFix = new ReplaceOctalNumericLiteralQuickFix();
              suffix = " not support this syntax. It requires '0o' prefix for octal literals";
            }
          }
        }
      }
    }
    commonRegisterProblem(message, suffix, len, node, quickFix);
  }

  @Override
  public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
    super.visitPyStringLiteralExpression(node);
    List<ASTNode> stringNodes = node.getStringNodes();

    for (ASTNode stringNode : stringNodes) {
      int len = 0;
      StringBuilder message = new StringBuilder(myCommonMessage);
      String nodeText = stringNode.getText();
      int index = PyStringLiteralExpressionImpl.getPrefixLength(nodeText);
      String prefix = nodeText.substring(0, index).toUpperCase();
      final TextRange range = TextRange.create(stringNode.getStartOffset(), stringNode.getStartOffset() + index);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (prefix.isEmpty()) continue;

        final Set<String> prefixes = AVAILABLE_PREFIXES.get(languageLevel);
        if (!prefixes.contains(prefix))
          len = appendLanguageLevel(message, len, languageLevel);
      }
      commonRegisterProblem(message, " not support a '" + prefix + "' prefix", len, node, range, new RemovePrefixQuickFix(prefix));
    }
  }

  @Override
  public void visitPyListCompExpression(final PyListCompExpression node) {
    super.visitPyListCompExpression(node);
    int len = 0;
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      boolean tmp = UnsupportedFeaturesUtil.visitPyListCompExpression(node, languageLevel);
      if (tmp) {
        len = appendLanguageLevel(message, len, languageLevel);
      }
    }
    for (ComprhForComponent forComponent : node.getForComponents()) {
      final PyExpression iteratedList = forComponent.getIteratedList();
      commonRegisterProblem(message, " not support this syntax in list comprehensions.", len, iteratedList,
                            new ReplaceListComprehensionsQuickFix());
    }
  }

  @Override
  public void visitPyRaiseStatement(PyRaiseStatement node) {
    super.visitPyRaiseStatement(node);
    // empty raise
    int len = 0;
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      boolean hasNoArgs = UnsupportedFeaturesUtil.raiseHasNoArgs(node, languageLevel);
      if (hasNoArgs) {
        len = appendLanguageLevel(message, len, languageLevel);
      }
    }
    commonRegisterProblem(message, " not support this syntax. Raise with no arguments can only be used in an except block",
                          len, node, null);
    // raise 1, 2, 3
    len = 0;
    message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      boolean hasTwoArgs = UnsupportedFeaturesUtil.raiseHasMoreThenOneArg(node, languageLevel);
      if (hasTwoArgs) {
        len = appendLanguageLevel(message, len, languageLevel);
      }
    }
    commonRegisterProblem(message, " not support this syntax.",
                          len, node, new ReplaceRaiseStatementQuickFix());

    // raise exception from cause
    len = 0;
    message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      final LanguageLevel languageLevel = myVersionsToProcess.get(i);
      final boolean hasFrom = UnsupportedFeaturesUtil.raiseHasFromKeyword(node, languageLevel);
      if (hasFrom) {
        len = appendLanguageLevel(message, len, languageLevel);
      }
    }
    commonRegisterProblem(message, " not support this syntax.",
                          len, node, new ReplaceRaiseStatementQuickFix());
  }

  @Override
  public void visitPyReprExpression(PyReprExpression node) {
    super.visitPyReprExpression(node);
    int len = 0;
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      if (languageLevel.isPy3K()) {
        len = appendLanguageLevel(message, len, languageLevel);
      }
    }
    commonRegisterProblem(message, " not support backquotes, use repr() instead",
                          len, node, new ReplaceBackquoteExpressionQuickFix());
  }


  @Override
  public void visitPyWithStatement(PyWithStatement node) {
    super.visitPyWithStatement(node);
    Set<PyWithItem> problemItems = new HashSet<PyWithItem>();
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      if (languageLevel == LanguageLevel.PYTHON24) {
        registerProblem(node, "Python version 2.4 doesn't support this syntax.");
      }
      else if (!languageLevel.supportsSetLiterals()) {
        final PyWithItem[] items = node.getWithItems();
        if (items.length > 1) {
          for (int j = 1; j < items.length; j++) {
            if (!problemItems.isEmpty())
              message.append(", ");
            message.append(languageLevel.toString());
            problemItems.add(items [j]);
          }
        }
      }
    }
    message.append(" do not support multiple context managers");
    for (PyWithItem item : problemItems) {
      registerProblem(item, message.toString());
    }
  }

  @Override
  public void visitPyClass(PyClass node) {    //PY-2719
    super.visitPyClass(node);
    if (myVersionsToProcess.contains(LanguageLevel.PYTHON24)) {
      PyArgumentList list = node.getSuperClassExpressionList();
      if (list != null && list.getArguments().length == 0)
        registerProblem(list, "Python version 2.4 does not support this syntax.");
    }
  }

  @Override
  public void visitPyPrintStatement(PyPrintStatement node) {
    super.visitPyPrintStatement(node);
    if (shouldBeCompatibleWithPy3()) {
      boolean hasProblem = false;
      PsiElement[] arguments = node.getChildren();
      for (PsiElement element : arguments) {
        if (!((element instanceof PyParenthesizedExpression) || (element instanceof PyTupleExpression))) {
          hasProblem = true;
          break;
        }
      }
      if (hasProblem || arguments.length == 0)
        registerProblem(node, "Python version >= 3.0 do not support this syntax. The print statement has been replaced with a print() function",
                          new CompatibilityPrintCallQuickFix());
    }
  }

  @Override
  public void visitPyFromImportStatement(PyFromImportStatement node) {
    super.visitPyFromImportStatement(node);
    PyReferenceExpression importSource  = node.getImportSource();
    if (importSource != null) {
      if (myVersionsToProcess.contains(LanguageLevel.PYTHON24)) {      //PY-2793
        PsiElement prev = importSource.getPrevSibling();
        if (prev != null && prev.getNode().getElementType() == PyTokenTypes.DOT)
          registerProblem(node, "Python version 2.4 doesn't support this syntax.");
      }
    }
    else {
      if (myVersionsToProcess.contains(LanguageLevel.PYTHON24))
        registerProblem(node, "Python version 2.4 doesn't support this syntax.");
    }
  }

  @Override
  public void visitPyAssignmentStatement(PyAssignmentStatement node) {
    super.visitPyAssignmentStatement(node);
    if (myVersionsToProcess.contains(LanguageLevel.PYTHON24)) {
      PyExpression assignedValue = node.getAssignedValue();

      Stack<PsiElement> st = new Stack<PsiElement>();           // PY-2796
      if (assignedValue != null)
        st.push(assignedValue);
      while (!st.isEmpty()) {
        PsiElement el = st.pop();
        if (el instanceof PyYieldExpression)
          registerProblem(node, "Python version 2.4 doesn't support this syntax. " +
                                                    "In Python <= 2.4, yield was a statement; it didn't return any value.");
        else {
          for (PsiElement e : el.getChildren())
            st.push(e);
        }
      }
    }
  }

  @Override
  public void visitPyConditionalExpression(PyConditionalExpression node) {   //PY-4293
    super.visitPyConditionalExpression(node);
    if (myVersionsToProcess.contains(LanguageLevel.PYTHON24)) {
      registerProblem(node, "Python version 2.4 doesn't support this syntax.");
    }
  }

  @Override
  public void visitPyTryExceptStatement(PyTryExceptStatement node) { // PY-2795
    super.visitPyTryExceptStatement(node);
    if (myVersionsToProcess.contains(LanguageLevel.PYTHON24)) {
      PyExceptPart[] excepts =  node.getExceptParts();
      PyFinallyPart finallyPart = node.getFinallyPart();
      if (excepts.length != 0 && finallyPart != null)
        registerProblem(node, "Python version 2.4 doesn't support this syntax. You could use a finally block to ensure " +
                                                "that code is always executed, or one or more except blocks to catch specific exceptions.");
    }
  }

  @Override
  public void visitPyCallExpression(PyCallExpression node) {
    super.visitPyCallExpression(node);
    int len = 0;
    StringBuilder message = new StringBuilder(myCommonMessage);

    if (myVersionsToProcess.contains(LanguageLevel.PYTHON24) || myVersionsToProcess.contains(LanguageLevel.PYTHON25)) {
      boolean hasStar = false;
      for (PyExpression argument : node.getArguments()) {
        if (hasStar && argument instanceof PyKeywordArgument) {
          registerProblem(argument, "Python version < 2.6 doesn't support this syntax. Named parameter cannot appear past *arg or **kwarg.");
        }
        if (argument instanceof PyStarArgument) hasStar = true;
      }
    }

    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);
      if (!languageLevel.isPy3K()) {
        final PsiElement firstChild = node.getFirstChild();
        if (firstChild != null) {
          final String name = firstChild.getText();
          if (PyNames.SUPER.equals(name)) {
            final PyArgumentList argumentList = node.getArgumentList();
            if (argumentList != null && argumentList.getArguments().length == 0) {
              len = appendLanguageLevel(message, len, languageLevel);
            }
          }
        }
      }
    }
    commonRegisterProblem(message, " not support this syntax. super() should have arguments in Python 2",
                          len, node, null);
  }

  @Override
  public void visitPyYieldExpression(PyYieldExpression node) {
    super.visitPyYieldExpression(node);
    if (!node.isDelegating()) {
      return;
    }
    for (LanguageLevel level : myVersionsToProcess) {
      if (level.isOlderThan(LanguageLevel.PYTHON33)) {
        registerProblem(node, "Python versions < 3.3 do not support this syntax. Delegating to a subgenerator is available since " +
                              "Python 3.3; use explicit iteration over subgenerator instead.");
        break;
      }
    }
  }

  @Override
  public void visitPyReturnStatement(PyReturnStatement node) {
    boolean allowed = true;
    for (LanguageLevel level : myVersionsToProcess) {
      if (level.isOlderThan(LanguageLevel.PYTHON33)) {
        allowed = false;
        break;
      }
    }
    if (allowed) {
      return;
    }
    final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, false, PyClass.class);
    if (function != null && node.getExpression() != null) {
      final YieldVisitor visitor = new YieldVisitor();
      function.acceptChildren(visitor);
      if (visitor.haveYield()) {
        registerProblem(node, "Python versions < 3.3 do not allow 'return' with argument inside generator.");
      }
    }
  }

  @Override
  public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node) {
    if (shouldBeCompatibleWithPy2() && node.isEllipsis()) {
      final PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(node, PySubscriptionExpression.class);
      if (subscription != null && PsiTreeUtil.isAncestor(subscription.getIndexExpression(), node, false)) {
        return;
      }
      final PySliceItem sliceItem = PsiTreeUtil.getParentOfType(node, PySliceItem.class);
      if (sliceItem != null) {
        return;
      }
      registerProblem(node, "Python versions < 3.0 do not support '...' outside of sequence slicings.");
    }
  }

  private static class YieldVisitor extends PyElementVisitor {
    private boolean _haveYield = false;

    public boolean haveYield() {
      return _haveYield;
    }

    @Override
    public void visitPyYieldExpression(final PyYieldExpression node) {
      _haveYield = true;
    }

    @Override
    public void visitPyElement(final PyElement node) {
      if (!_haveYield) {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      // do not go to nested functions
    }
  }

  protected abstract void registerProblem(PsiElement node, String s, @Nullable LocalQuickFix localQuickFix, boolean asError);
  protected abstract void registerProblem(PsiElement node, TextRange range, String s, @Nullable LocalQuickFix localQuickFix, boolean asError);

  protected void registerProblem(final PsiElement node, final String s, @Nullable final LocalQuickFix localQuickFix) {
    registerProblem(node, s, localQuickFix, true);
  }

  protected void registerProblem(final PsiElement node, final String s) {
    registerProblem(node, s, null);
  }

  protected void setVersionsToProcess(List<LanguageLevel> versionsToProcess) {
    myVersionsToProcess = versionsToProcess;
  }

  protected void commonRegisterProblem(StringBuilder initMessage, String suffix,
                                       int len, PyElement node, LocalQuickFix localQuickFix) {
    commonRegisterProblem(initMessage, suffix, len, node, node.getTextRange(), localQuickFix, true);
  }

  protected void commonRegisterProblem(StringBuilder initMessage, String suffix,
                                       int len, PyElement node, TextRange range, LocalQuickFix localQuickFix) {
    commonRegisterProblem(initMessage, suffix, len, node, range, localQuickFix, true);
  }

  protected void commonRegisterProblem(StringBuilder initMessage, String suffix,
                                       int len, PyElement node, TextRange range, @Nullable LocalQuickFix localQuickFix, boolean asError) {
    initMessage.append(" do");
    if (len == 1)
      initMessage.append("es");
    initMessage.append(suffix);
    if (len != 0)
      registerProblem(node, range, initMessage.toString(), localQuickFix, asError);
  }

  protected void commonRegisterProblem(StringBuilder initMessage, String suffix,
                                       int len, PyElement node, @Nullable LocalQuickFix localQuickFix, boolean asError) {
    initMessage.append(" do");
    if (len == 1)
      initMessage.append("es");
    initMessage.append(suffix);
    if (len != 0)
      registerProblem(node, node.getTextRange(), initMessage.toString(), localQuickFix, asError);
  }

  protected static int appendLanguageLevel(StringBuilder message, int len, LanguageLevel languageLevel) {
    if (len != 0)
      message.append(", ");
    message.append(languageLevel.toString());
    return ++len;
  }

  @Override
  public void visitPyNonlocalStatement(final PyNonlocalStatement node) {
    if (shouldBeCompatibleWithPy2()) {
      registerProblem(node, "nonlocal keyword available only since py3", null, false);
    }
  }

  protected boolean shouldBeCompatibleWithPy2() {
    for (LanguageLevel level : myVersionsToProcess) {
      if (level.isOlderThan(LanguageLevel.PYTHON30)) {
        return true;
      }
    }
    return false;
  }

  protected boolean shouldBeCompatibleWithPy3() {
    for (LanguageLevel level : myVersionsToProcess) {
      if (level.isPy3K()) {
        return true;
      }
    }
    return false;
  }
}
