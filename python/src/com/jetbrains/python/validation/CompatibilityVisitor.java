package com.jetbrains.python.validation;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * User : catherine
 */
public abstract class CompatibilityVisitor extends PyAnnotator {
  protected List<LanguageLevel> myVersionsToProcess;
  private String myCommonMessage = "Python version ";

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
        final PyQualifiedName qName = importElement.getImportedQName();
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
    int len = 0;
    StringBuilder message = new StringBuilder(myCommonMessage);
    for (int i = 0; i != myVersionsToProcess.size(); ++i) {
      LanguageLevel languageLevel = myVersionsToProcess.get(i);

      if (languageLevel.isPy3K()) {
        final String text = node.getText();
        if (text.startsWith("u") || text.startsWith("U")) {
          len = appendLanguageLevel(message, len, languageLevel);
        }
      }
    }
    commonRegisterProblem(message, " not support a leading \'u\' or \'U\'.", len, node, new RemoveLeadingUQuickFix());
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
    if (myVersionsToProcess.contains(LanguageLevel.PYTHON30) || myVersionsToProcess.contains(LanguageLevel.PYTHON31)) {
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
      if (assignedValue instanceof PyConditionalExpression)                        // PY-2792
        registerProblem(node, "Python version 2.4 doesn't support this syntax.");

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

  protected abstract void registerProblem(PsiElement node, String s, LocalQuickFix localQuickFix, boolean asError);

  protected void registerProblem(PsiElement node, String s, LocalQuickFix localQuickFix) {
    registerProblem(node, s, localQuickFix, true);
  }

  protected void registerProblem(PsiElement node, String s) {
    registerProblem(node, s, null);
  }

  protected void setVersionsToProcess(List<LanguageLevel> versionsToProcess) {
    myVersionsToProcess = versionsToProcess;
  }

  protected void commonRegisterProblem(StringBuilder initMessage, String suffix,
                                       int len, PyElement node, LocalQuickFix localQuickFix) {
    commonRegisterProblem(initMessage, suffix, len, node, localQuickFix, true);
  }

  protected void commonRegisterProblem(StringBuilder initMessage, String suffix,
                                       int len, PyElement node, LocalQuickFix localQuickFix, boolean asError) {
    initMessage.append(" do");
    if (len == 1)
      initMessage.append("es");
    initMessage.append(suffix);
    if (len != 0)
      registerProblem(node, initMessage.toString(), localQuickFix, asError);
  }

  protected static int appendLanguageLevel(StringBuilder message, int len, LanguageLevel languageLevel) {
    if (len != 0)
      message.append(", ");
    message.append(languageLevel.toString());
    return ++len;
  }

  @Override
  public void visitPyNonlocalStatement(PyNonlocalStatement node) {
    registerProblem(node, "nonlocal keyword available only since py3", null, false);
  }
}
