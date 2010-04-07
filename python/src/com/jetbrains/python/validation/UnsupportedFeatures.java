package com.jetbrains.python.validation;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.intentions.*;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 16.02.2010
 * Time: 16:16:45
 */
public class UnsupportedFeatures extends PyAnnotator {
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
    if (isPy2(node)) {
      getHolder().createWarningAnnotation(node, "Dictionary comprehension is not supported in Python 2").registerFix(new ConvertDictCompIntention());
    }
  }

  @Override
  public void visitPySetLiteralExpression(PySetLiteralExpression node) {
    LanguageLevel languageLevel = getLanguageLevel(node);
    if (languageLevel == LanguageLevel.PYTHON24
        || languageLevel == LanguageLevel.PYTHON25
        || languageLevel == LanguageLevel.PYTHON26) {
      getHolder().createWarningAnnotation(node, "This Python version does not support set literal expressions").registerFix(new ConvertSetLiteralIntention());
    }
  }

  @Override
  public void visitPyExceptBlock(PyExceptPart node) {
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
    PyImportElement[] importElements = node.getImportElements();
    for (PyImportElement importElement : importElements) {
      PyReferenceExpression importReference = importElement.getImportReference();
      if (importReference != null) {
        String name = importReference.getName();
        if (isPy2(node)) {
          if ("builtins".equals(name)) {
            getHolder().createWarningAnnotation(node, "There is no module builtins in Python 2")
              .registerFix(new ReplaceBuiltinsIntention());
          }
        }
        else {
          if ("__builtin__".equals(name)) {
            getHolder().createWarningAnnotation(node, "Module __builtin__ renamed to builtins").registerFix(new ReplaceBuiltinsIntention());
          }
        }
      }
    }
  }

  @Override
  public void visitPyCallExpression(PyCallExpression node) {
    if (isPy2(node)) {
      PsiElement firstChild = node.getFirstChild();
      if (firstChild != null) {
        String name = firstChild.getText();
        if ("super".equals(name)) {
          PyArgumentList argumentList = node.getArgumentList();
          if (argumentList != null && argumentList.getArguments().length == 0) {
            getHolder().createWarningAnnotation(node, "super() should have arguments in Python 2");
          }
        }
      }
    } else {
      String name = node.getCallee().getName();
      if ("raw_input".equals(name)) {
        getHolder().createWarningAnnotation(node.getCallee(), PyBundle.message("ANN.method.$0.removed.use.$1", name, "input")).
                        registerFix(new ReplaceMethodIntention("input"));
      }
      else if (REMOVED_METHODS.contains(name)) {
        getHolder().createWarningAnnotation(node.getCallee(), PyBundle.message("ANN.method.$0.removed", name));
      }
    }
  }

  @Override
  public void visitPyStarExpression(PyStarExpression node) {
    if (isPy2(node)) {
      getHolder().createWarningAnnotation(node, "Python 2 does not support star expressions");
    }
  }

  @Override
  public void visitPyBinaryExpression(PyBinaryExpression node) {
    if (node.isOperator("<>")) {
      String message = isPy3K(node) ? "<> is not supported in Python 3, use != instead" : "<> is deprecated, use != instead";
      getHolder().createWarningAnnotation(node, message).registerFix(new ReplaceNotEqOperatorIntention());
    }
  }

  @Override
  public void visitPyNumericLiteralExpression(PyNumericLiteralExpression node) {
    if (isPy3K(node)) {
      String text = node.getText();
      if (text.endsWith("l") || text.endsWith("L")) {
        getHolder().createWarningAnnotation(node,
                                            "Integer literals do not support a trailing \'l\' or \'L\' in Python 3").registerFix(new RemoveTrailingLIntention());
      }
      if (text.length() > 1 && text.charAt(0) == '0') {
        char c = Character.toLowerCase(text.charAt(1));
        if (c != 'o' && c != 'b' && c != 'x') {
          getHolder().createWarningAnnotation(node,
                                              "Python 3 requires '0o' prefix for octal literals").registerFix(new ReplaceOctalNumericLiteralIntention());
        }
      }
    }
  }

  @Override
  public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
    if (isPy3K(node)) {
      String text = node.getText();
      if (text.startsWith("u") || text.startsWith("U")) {
        getHolder().createWarningAnnotation(node,
                                            "String literals do not support a leading \'u\' or \'U\' in Python 3").registerFix(new RemoveLeadingUIntention());
      }
    }
  }

  @Override
  public void visitPyListCompExpression(PyListCompExpression node) {
    List<ComprhForComponent> forComponents = node.getForComponents();
    for (ComprhForComponent forComponent : forComponents) {
      PyExpression iteratedList = forComponent.getIteratedList();
      if (iteratedList instanceof PyTupleExpression) {
        getHolder().createWarningAnnotation(iteratedList,
                                            "List comprehensions do not support this syntax in Python 3").registerFix(new ReplaceListComprehensionsIntention());
      }
    }
  }

  @Override
  public void visitPyRaiseStatement(PyRaiseStatement node) {
    PyExpression[] expressions = node.getExpressions();
    if (expressions != null) {
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
  }

  @Override
  public void visitPyReprExpression(PyReprExpression node) {
    if (isPy3K(node)) {
      getHolder().createWarningAnnotation(node, "Backquote is not supported in Python 3, use repr() instead").registerFix(new ReplaceBackquoteExpressionIntention());
    }
  }
}
