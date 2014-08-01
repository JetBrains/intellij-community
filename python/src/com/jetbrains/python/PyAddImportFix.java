package com.jetbrains.python;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix that adds import to file
 *
 * @author Ilya.Kazakevich
 */
public class PyAddImportFix implements LocalQuickFix {
  @NotNull
  private final String myImportToAdd;
  @NotNull
  private final PyFile myFile;

  /**
   * @param importToAdd string representing what to add (i.e. "from foo import bar")
   * @param file where to add
   */
  public PyAddImportFix(@NotNull final String importToAdd, @NotNull final PyFile file) {
    myImportToAdd = importToAdd;
    myFile = file;
  }

  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("QFIX.add.import", myImportToAdd);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final PyImportStatementBase statement =
      generator.createFromText(LanguageLevel.forElement(myFile), PyImportStatementBase.class, myImportToAdd);
    final PsiElement recommendedPosition = AddImportHelper.getFileInsertPosition(myFile);
    myFile.addAfter(statement, recommendedPosition);
  }
}

