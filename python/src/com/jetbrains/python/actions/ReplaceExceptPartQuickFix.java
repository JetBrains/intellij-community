package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyTryExceptStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 10.02.2010
 * Time: 19:24:17
 */
public class ReplaceExceptPartQuickFix implements LocalQuickFix {
  private final boolean myPy3KFlag;

  public ReplaceExceptPartQuickFix(boolean py3KFlag) {
    myPy3KFlag = py3KFlag;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.replace.except.part");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyExceptPart exceptPart = (PyExceptPart) descriptor.getPsiElement();
    PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    PsiElement element = exceptPart.getExceptClass().getNextSibling();
    while (element instanceof PsiWhiteSpace) {
      element = element.getNextSibling();
    }
    assert element != null;
    if (myPy3KFlag) {
      PyTryExceptStatement newElement = elementGenerator.createFromText(project, PyTryExceptStatement.class, "try:  pass except a as b:  pass");
      ASTNode node = newElement.getExceptParts()[0].getNode().findChildByType(PyTokenTypes.AS_KEYWORD);
      assert node != null;
      element.replace(node.getPsi());
    } else {
      element.replace(elementGenerator.createComma(project).getPsi());
    }
  }
}