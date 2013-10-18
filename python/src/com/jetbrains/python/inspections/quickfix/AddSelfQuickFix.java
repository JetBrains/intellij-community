package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Insert 'self' in a method that lacks any arguments
 * User: dcheryasov
 * Date: Nov 19, 2008
 */
public class AddSelfQuickFix implements LocalQuickFix {
  private final String myParamName;

  public AddSelfQuickFix(String paramName) {
    myParamName = paramName;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.add.parameter.self", myParamName);
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return "Add parameter";
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement problem_elt = descriptor.getPsiElement();
    if (problem_elt instanceof PyParameterList) {
      final PyParameterList param_list = (PyParameterList)problem_elt;
      if (!FileModificationService.getInstance().preparePsiElementForWrite(problem_elt)) {
        return;
      }
      PyNamedParameter new_param = PyElementGenerator.getInstance(project).createParameter(myParamName);
      param_list.addParameter(new_param);
    }
  }
}
