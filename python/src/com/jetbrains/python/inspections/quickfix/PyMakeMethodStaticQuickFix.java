package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: ktisha
 */
public class PyMakeMethodStaticQuickFix implements LocalQuickFix {
  public PyMakeMethodStaticQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.make.static");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    PyUtil.deleteParameter(problemFunction, 0);

    final PyDecoratorList problemDecoratorList = problemFunction.getDecoratorList();
    List<String> decoTexts = new ArrayList<String>();
    decoTexts.add("@staticmethod");
    if (problemDecoratorList != null) {
      final PyDecorator[] decorators = problemDecoratorList.getDecorators();
      for (PyDecorator deco : decorators) {
        decoTexts.add(deco.getText());
      }
    }

    PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final PyDecoratorList decoratorList = generator.createDecoratorList(decoTexts.toArray(new String[decoTexts.size()]));

    if (problemDecoratorList != null) {
      problemDecoratorList.replace(decoratorList);
    }
    else {
      problemFunction.addBefore(decoratorList, problemFunction.getFirstChild());
    }
  }
}
