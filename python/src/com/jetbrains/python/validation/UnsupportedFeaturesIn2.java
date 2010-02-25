package com.jetbrains.python.validation;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.codeInsight.intentions.ConvertDictCompIntention;
import com.jetbrains.python.codeInsight.intentions.ConvertSetLiteralIntention;
import com.jetbrains.python.codeInsight.intentions.ReplaceBuiltinsIntention;
import com.jetbrains.python.codeInsight.intentions.ReplaceExceptPartIntention;
import com.jetbrains.python.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 16.02.2010
 * Time: 16:16:45
 */
public class UnsupportedFeaturesIn2 extends PyAnnotator {

  private static boolean isPy2(PyElement node) {
    VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
    return virtualFile != null && !LanguageLevel.forFile(virtualFile).isPy3K();
  }

  private static boolean isPy3K(PyElement node) {
    VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
    return virtualFile != null && LanguageLevel.forFile(virtualFile).isPy3K();
  }

  @Override
  public void visitPyDictCompExpression(PyDictCompExpression node) {
    if (isPy2(node)) {
      getHolder().createWarningAnnotation(node, "Dictionary comprehension not supported in Python2").registerFix(new ConvertDictCompIntention());
    }
  }

  @Override
  public void visitPySetLiteralExpression(PySetLiteralExpression node) {
    if (isPy2(node)) {
      getHolder().createWarningAnnotation(node, "Python2 not supported set literal expressions").registerFix(new ConvertSetLiteralIntention());
    }
  }

  @Override
  public void visitPyExceptBlock(PyExceptPart node) {
    if (isPy2(node)) {
      PyExpression exceptClass = node.getExceptClass();
      if (exceptClass != null) {
        PsiElement element = exceptClass.getNextSibling();
        while (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
        }
        if (element != null && "as".equals(element.getText())) {
          getHolder().createWarningAnnotation(node, "Python2 not supported such syntax").registerFix(new ReplaceExceptPartIntention());
        }
      }
    }
  }

  @Override
  public void visitPyImportStatement(PyImportStatement node) {
    if (isPy2(node)) {
      PyImportElement[] importElements = node.getImportElements();
      for (PyImportElement importElement : importElements) {
        PyReferenceExpression importReference = importElement.getImportReference();
        if (importReference != null) {
          String name = importReference.getName();
          if ("builtins".equals(name)) {
            getHolder().createWarningAnnotation(node, "There is no module builtins in Python2").registerFix(new ReplaceBuiltinsIntention());
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
            getHolder().createWarningAnnotation(node, "super() should have arguments in Python2");
          }
        }
      }
    }
  }
}
