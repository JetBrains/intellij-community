package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
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
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.add.parameter.self");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement problem_elt = descriptor.getPsiElement();
    if (problem_elt instanceof PyParameterList) {
      final PyParameterList param_list = (PyParameterList)problem_elt;
      if (!CodeInsightUtilBase.preparePsiElementForWrite(problem_elt)) {
        return;
      }
      Language language = problem_elt.getLanguage();
      if (language instanceof PythonLanguage) {
        final PythonLanguage pythonLanguage = (PythonLanguage)language;
        PyElementGenerator generator = pythonLanguage.getElementGenerator();
        // TODO: generalize, move to generator
        PyNamedParameter new_param = generator.createFromText(project, PyNamedParameter.class, "def f(self): pass", new int[]{0, 3, 1});
        param_list.addParameter(new_param);
      }
    }
  }
}
