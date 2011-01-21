package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyTryExceptStatement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class ReplaceExceptPartQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.convert.except.to");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement exceptPart = descriptor.getPsiElement();
    if (exceptPart instanceof PyExceptPart) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      PsiElement element = ((PyExceptPart)exceptPart).getExceptClass().getNextSibling();
      while (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }
      assert element != null;
      PyTryExceptStatement newElement =
        elementGenerator.createFromText(LanguageLevel.forElement(exceptPart), PyTryExceptStatement.class, "try:  pass except a as b:  pass");
      ASTNode node = newElement.getExceptParts()[0].getNode().findChildByType(PyTokenTypes.AS_KEYWORD);
      assert node != null;
      element.replace(node.getPsi());
    }
  }

}
