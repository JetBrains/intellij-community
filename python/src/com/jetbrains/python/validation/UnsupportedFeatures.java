package com.jetbrains.python.validation;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.intentions.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class UnsupportedFeatures extends PyAnnotator {
  /*
  private static final Set<String> REMOVED_METHODS = new HashSet<String>();

  static {
    REMOVED_METHODS.add("cmp");
    REMOVED_METHODS.add("apply");
    REMOVED_METHODS.add("callable");
    REMOVED_METHODS.add("coerce");
    REMOVED_METHODS.add("execfile");
    REMOVED_METHODS.add("reduce");
    REMOVED_METHODS.add("reload");
  }
  */

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

  @Override
  public void visitPyDictCompExpression(PyDictCompExpression node) {
    super.visitPyDictCompExpression(node);
    LanguageLevel languageLevel = getLanguageLevel(node);
    if (!languageLevel.supportsSetLiterals()) {
      getHolder()
        .createWarningAnnotation(node, "Python version " + languageLevel + " does not support dictionary comprehensions")
        .registerFix(new ConvertDictCompIntention());
    }
  }

  @Override
  public void visitPySetLiteralExpression(PySetLiteralExpression node) {
    super.visitPySetLiteralExpression(node);
    LanguageLevel languageLevel = getLanguageLevel(node);
    if (!languageLevel.supportsSetLiterals()) {
      getHolder()
        .createWarningAnnotation(node, "Python version " + languageLevel + " does not support set literal expressions")
        .registerFix(new ConvertSetLiteralIntention());
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
          getHolder().createWarningAnnotation(node, "Python 3 does not support this syntax").registerFix(new ReplaceExceptPartIntention());
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
            getHolder().createWarningAnnotation(node, "There is no module builtins in Python 2")
              .registerFix(new ReplaceBuiltinsIntention());
          }
        }
        else {
          if (qName.matches("__builtin__")) {
            getHolder().createWarningAnnotation(node, "Module __builtin__ renamed to builtins").registerFix(new ReplaceBuiltinsIntention());
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
    /* incorrectly working functionality temporarily removed (PY-1424, PY-1820)
    else {
      final String name = node.getCallee().getName();
      if ("raw_input".equals(name)) {
        getHolder().createWarningAnnotation(node.getCallee(), PyBundle.message("ANN.method.$0.removed.use.$1", name, "input")).
                        registerFix(new ReplaceMethodIntention("input"));
      }
      else if (REMOVED_METHODS.contains(name)) {
        getHolder().createWarningAnnotation(node.getCallee(), PyBundle.message("ANN.method.$0.removed", name));
      }
    }
    */
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
    if (node.isOperator("<>")) {
      final String message = isPy3K(node) ? "<> is not supported in Python 3, use != instead" : "<> is deprecated, use != instead";
      getHolder().createWarningAnnotation(node, message).registerFix(new ReplaceNotEqOperatorIntention());
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
            getHolder().createWarningAnnotation(node,
                                              "Python 3 requires '0o' prefix for octal literals")
              .registerFix(new ReplaceOctalNumericLiteralIntention());
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
        getHolder().createWarningAnnotation(node,
                                            "String literals do not support a leading \'u\' or \'U\' in Python 3")
          .registerFix(new RemoveLeadingUIntention());
      }
    }
  }

  @Override
  public void visitPyListCompExpression(final PyListCompExpression node) {
    super.visitPyListCompExpression(node);
    final List<ComprhForComponent> forComponents = node.getForComponents();
    for (ComprhForComponent forComponent : forComponents) {
      final PyExpression iteratedList = forComponent.getIteratedList();
      if (iteratedList instanceof PyTupleExpression) {
        getHolder().createWarningAnnotation(iteratedList,
                                            "List comprehensions do not support this syntax in Python 3").registerFix(new ReplaceListComprehensionsIntention());
      }
    }
  }

  @Override
  public void visitPyRaiseStatement(PyRaiseStatement node) {
    super.visitPyRaiseStatement(node);
    final PyExpression[] expressions = node.getExpressions();
    if (expressions.length > 0) {
      if (expressions.length < 2) {
        return;
      }

      if (isPy3K(node)) {
        if (expressions.length == 3) {
          getHolder().createWarningAnnotation(node, "Python 3 does not support this syntax")
            .registerFix(new ReplaceRaiseStatementIntention());
          return;
        }
        PsiElement element = expressions[0].getNextSibling();
        while (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
        }
        if (element != null && ",".equals(element.getText())) {
          getHolder().createWarningAnnotation(node, "Python 3 does not support this syntax")
            .registerFix(new ReplaceRaiseStatementIntention());
        }
      }
    }
    else if (isPy3K(node)) {
      final PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class);
      if (exceptPart == null) {
        getHolder().createErrorAnnotation(node, "raise with no arguments can only be used in an except block");
      }
    }
  }

  @Override
  public void visitPyReprExpression(PyReprExpression node) {
    super.visitPyReprExpression(node);
    if (isPy3K(node)) {
      getHolder().createWarningAnnotation(node, "Backquote is not supported in Python 3, use repr() instead").registerFix(new ReplaceBackquoteExpressionIntention());
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
}
