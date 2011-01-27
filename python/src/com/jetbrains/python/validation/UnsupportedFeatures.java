package com.jetbrains.python.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonProblemDescriptorImpl;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.*;
import com.jetbrains.python.codeInsight.intentions.RemoveTrailingLIntention;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class UnsupportedFeatures extends PyAnnotator {
  @NotNull
  private static LanguageLevel getLanguageLevel(PyElement node) {
    VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
    if (virtualFile != null) {
      return LanguageLevel.forFile(virtualFile);
    }
    return LanguageLevel.getDefault();
  }

  private static boolean isPy2(PyElement node) {
    return !getLanguageLevel(node).isPy3K();
  }

  private static boolean isPy3K(PyElement node) {
    return getLanguageLevel(node).isPy3K();
  }

  private static IntentionAction createIntention(PyElement node, String message, LocalQuickFix fix) {
    LocalQuickFix[] quickFixes = {fix};
    CommonProblemDescriptorImpl descr = new ProblemDescriptorImpl(node, node, message,
                                                                  quickFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true,
                                                                  node.getTextRange(), true);
    return QuickFixWrapper.wrap((ProblemDescriptor)descr, 0);
  }

  @Override
  public void visitPyDictCompExpression(PyDictCompExpression node) {
    super.visitPyDictCompExpression(node);
    LanguageLevel languageLevel = getLanguageLevel(node);
    if (!languageLevel.supportsSetLiterals()) {
      String message = "Python version " + languageLevel + " does not support dictionary comprehensions";
      getHolder()
        .createWarningAnnotation(node, message)
        .registerFix(createIntention(node, message, new ConvertDictCompQuickFix()));
    }
  }

  @Override
  public void visitPySetLiteralExpression(PySetLiteralExpression node) {
    super.visitPySetLiteralExpression(node);
    LanguageLevel languageLevel = getLanguageLevel(node);
    if (!languageLevel.supportsSetLiterals()) {
      String message = "Python version " + languageLevel + " does not support set literal expressions";
      getHolder()
        .createWarningAnnotation(node, message)
        .registerFix(createIntention(node, message, new ConvertSetLiteralQuickFix()));
    }
  }

  @Override
  public void visitPySetCompExpression(PySetCompExpression node) {
    super.visitPySetCompExpression(node);
    final LanguageLevel languageLevel = getLanguageLevel(node);
    if (!languageLevel.supportsSetLiterals()) {
      getHolder().createWarningAnnotation(node, "Python version " + languageLevel + " does not support set comprehensions");
    }
  }

  @Override
  public void visitPyExceptBlock(PyExceptPart node) {
    super.visitPyExceptBlock(node);
    PyExpression exceptClass = node.getExceptClass();
    if (exceptClass != null) {
      LanguageLevel languageLevel = getLanguageLevel(node);
      if (languageLevel == LanguageLevel.PYTHON24 || languageLevel == LanguageLevel.PYTHON25) {
        PsiElement element = exceptClass.getNextSibling();
        while (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
        }
        if (element != null && "as".equals(element.getText())) {
          getHolder().createWarningAnnotation(node, "This Python version does not support this syntax");
        }
      }
      else if (isPy3K(node)) {
        PsiElement element = exceptClass.getNextSibling();
        while (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
        }
        if (element != null && ",".equals(element.getText())) {
          String message = "Python 3 does not support this syntax";
          getHolder().createWarningAnnotation(node, message).registerFix(createIntention(node, message, new ReplaceExceptPartQuickFix()));
        }
      }
    }
  }

  @Override
  public void visitPyImportStatement(PyImportStatement node) {
    super.visitPyImportStatement(node);
    PyImportElement[] importElements = node.getImportElements();
    for (PyImportElement importElement : importElements) {
      final PyQualifiedName qName = importElement.getImportedQName();
      if (qName != null) {
        if (isPy2(node)) {
          if (qName.matches("builtins")) {
            String message = "There is no module builtins in Python 2";
            getHolder().createWarningAnnotation(node, message)
              .registerFix(createIntention(node, message, new ReplaceBuiltinsQuickFix()));
          }
        }
        else {
          if (qName.matches("__builtin__")) {
            String message =  "Module __builtin__ renamed to builtins";
            getHolder().createWarningAnnotation(node, "Module __builtin__ renamed to builtins")
              .registerFix(createIntention(node, message, new ReplaceBuiltinsQuickFix()));
          }
        }
      }
    }
  }

  @Override
  public void visitPyCallExpression(PyCallExpression node) {
    super.visitPyCallExpression(node);
    if (isPy2(node)) {
      final PsiElement firstChild = node.getFirstChild();
      if (firstChild != null) {
        final String name = firstChild.getText();
        if (PyNames.SUPER.equals(name)) {
          final PyArgumentList argumentList = node.getArgumentList();
          if (argumentList != null && argumentList.getArguments().length == 0) {
            getHolder().createWarningAnnotation(node, "super() should have arguments in Python 2");
          }
        }
      }
    }
    else {
      PyExpression callee = node.getCallee();
      assert callee != null;
      PsiReference reference = callee.getReference();
      if (reference != null) {
        PsiElement resolved = reference.resolve();
        if (resolved == null) {
          final String name = callee.getText();
          if (!name.equals("print") && UnsupportedFeaturesUtil.BUILTINS.get(getLanguageLevel(callee)).contains(name)) {
            getHolder().createWarningAnnotation(callee, PyBundle.message("ANN.method.$0.removed", name));
          }
        }
      }
    }
  }

  @Override
  public void visitPyStarExpression(PyStarExpression node) {
    super.visitPyStarExpression(node);
    if (isPy2(node)) {
      getHolder().createWarningAnnotation(node, "Starred expressions are not allowed as assignment targets in Python 2");
    }
  }

  @Override
  public void visitPyBinaryExpression(PyBinaryExpression node) {
    super.visitPyBinaryExpression(node);
    if (isPy3K(node) && node.isOperator("<>")) {
      final String message = isPy3K(node) ? "<> is not supported in Python 3, use != instead" : "<> is deprecated, use != instead";
      getHolder().createWarningAnnotation(node, message).registerFix(createIntention(node, message, new ReplaceNotEqOperatorQuickFix()));
    }
  }

  @Override
  public void visitPyNumericLiteralExpression(final PyNumericLiteralExpression node) {
    super.visitPyNumericLiteralExpression(node);
    if (isPy3K(node)) {
      if (!node.isIntegerLiteral()) {
        return;
      }
      final String text = node.getText();
      if (text.endsWith("l") || text.endsWith("L")) {
        getHolder().createWarningAnnotation(node,
                                            "Integer literals do not support a trailing \'l\' or \'L\' in Python 3")
          .registerFix(new RemoveTrailingLIntention());
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
            final String message = "Python 3 requires '0o' prefix for octal literals";
            getHolder().createWarningAnnotation(node, message).registerFix(createIntention(node, message, new ReplaceOctalNumericLiteralQuickFix()));
          }
        }
      }
    }
  }

  @Override
  public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
    super.visitPyStringLiteralExpression(node);
    if (isPy3K(node)) {
      final String text = node.getText();
      if (text.startsWith("u") || text.startsWith("U")) {
        final String message = "String literals do not support a leading \'u\' or \'U\' in Python 3";
        getHolder().createWarningAnnotation(node, message).registerFix(createIntention(node, message, new RemoveLeadingUQuickFix()));
      }
    }
  }

  @Override
  public void visitPyListCompExpression(final PyListCompExpression node) {
    super.visitPyListCompExpression(node);
    if (isPy3K(node)) {
      final List<ComprhForComponent> forComponents = node.getForComponents();
      for (ComprhForComponent forComponent : forComponents) {
        final PyExpression iteratedList = forComponent.getIteratedList();
        if (iteratedList instanceof PyTupleExpression) {
          final String message = "List comprehensions do not support this syntax in Python 3";
          getHolder().createWarningAnnotation(iteratedList, message).registerFix(createIntention(iteratedList, message, new ReplaceListComprehensionsQuickFix()));
        }
      }
    }
  }

  @Override
  public void visitPyRaiseStatement(PyRaiseStatement node) {
    super.visitPyRaiseStatement(node);
    boolean hasProblem = UnsupportedFeaturesUtil.raiseHasNoArgs(node, LanguageLevel.forElement(node));
    if (hasProblem) {
      getHolder().createErrorAnnotation(node, "raise with no arguments can only be used in an except block");
    }
    hasProblem = UnsupportedFeaturesUtil.raiseHasMoreThenOneArg(node, LanguageLevel.forElement(node));
    if (hasProblem) {
      final String message = "Python 3 does not support this syntax";
      getHolder().createWarningAnnotation(node, message).registerFix(createIntention(node, message, new ReplaceRaiseStatementQuickFix()));
    }
  }

  @Override
  public void visitPyReprExpression(PyReprExpression node) {
    super.visitPyReprExpression(node);
    if (isPy3K(node)) {
      final String message = "Backquote is not supported in Python 3, use repr() instead";
      getHolder().createWarningAnnotation(node, message).registerFix(createIntention(node, message, new ReplaceBackquoteExpressionQuickFix()));
    }
  }

  @Override
  public void visitPyWithStatement(PyWithStatement node) {
    super.visitPyWithStatement(node);
    final LanguageLevel languageLevel = getLanguageLevel(node);
    if (!languageLevel.supportsSetLiterals()) {
      final PyWithItem[] items = node.getWithItems();
      if (items.length > 1) {
        for (int i = 1; i < items.length; i++) {
          getHolder().createWarningAnnotation(items [i], "Python version " + languageLevel + " does not support multiple context managers");
        }
      }
    }
  }
  @Override
  public void visitPyClass(PyClass node) {  //PY-2719
    if (getLanguageLevel(node) == LanguageLevel.PYTHON24) {
      PyArgumentList list = node.getSuperClassExpressionList();
      if (list != null && list.getArguments().length == 0)
        getHolder().createWarningAnnotation(list, "Python version 2.4 does not support this syntax.");
    }
  }

  @Override
  public void visitPyPrintStatement(PyPrintStatement node) {
    PsiElement[] arguments = node.getChildren();
    if (getLanguageLevel(node).isPy3K()) {
      for (PsiElement element : arguments) {
        if (!((element instanceof PyParenthesizedExpression) || (element instanceof PyTupleExpression)))
          getHolder().createWarningAnnotation(element, "Python versions >= 3.0 do not support this syntax. The print statement has been replaced with a print() function");
      }
    }
  }
  @Override
  public void visitPyFromImportStatement(PyFromImportStatement node) {
    PyReferenceExpression importSource  = node.getImportSource();
    if (importSource == null) {
      if (getLanguageLevel(node) == LanguageLevel.PYTHON24)
        getHolder().createWarningAnnotation(node, "Python version 2.4 doesn't support this syntax.");
    }
  }
  @Override
  public void visitPyAssignmentStatement(PyAssignmentStatement node) { // PY-2792
    if (getLanguageLevel(node) == LanguageLevel.PYTHON24) {
      if (node.getAssignedValue() instanceof PyConditionalExpression)
        getHolder().createWarningAnnotation(node, "Python version 2.4 doesn't support this syntax.");
    }
  }
  @Override
  public void visitPyTryExceptStatement(PyTryExceptStatement node) {   // PY-2795
    if (getLanguageLevel(node) == LanguageLevel.PYTHON24) {
      PyExceptPart[] excepts =  node.getExceptParts();
      PyFinallyPart finallyPart = node.getFinallyPart();
      if (excepts.length != 0 && finallyPart != null)
        getHolder().createWarningAnnotation(node, "Python version 2.4 doesn't support this syntax. You could use a finally block to ensure " +
                                                  "that code is always executed, or one or more except blocks to catch specific exceptions.");
    }
  }
}
