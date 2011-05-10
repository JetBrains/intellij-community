package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class AddNoinspectionQuickFix implements LocalQuickFix {
  String myInspectionName;
  PyElement myInspectionElement;

  public AddNoinspectionQuickFix(String name, PyElement element) {
    myInspectionElement = element;
    myInspectionName = name;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.add.noinspection");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    if (myInspectionElement == null) return;
    final PsiComment noInspection =
      PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PsiComment.class,
                                                             "#noinspection " + myInspectionName);
    myInspectionElement.getParent().addBefore(noInspection, myInspectionElement);
  }
}
