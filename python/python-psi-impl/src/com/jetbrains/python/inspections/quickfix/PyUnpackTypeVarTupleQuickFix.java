package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import org.jetbrains.annotations.NotNull;

public class PyUnpackTypeVarTupleQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.unpack.type.var.tuple");
  }

  public static void replaceToTypingExtensionsUnpack(@NotNull PsiElement elementTpReplace, @NotNull PsiElement elementInUnpack,
                                            @NotNull PsiFile file, @NotNull Project project) {
    AddImportHelper.addOrUpdateFromImportStatement(file, "typing_extensions", "Unpack", null,
                                                   AddImportHelper.ImportPriority.FUTURE, null);

    String unpacked = "Unpack[" + elementInUnpack.getText() + "]";
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyExpressionStatement expressionStatement =
      elementGenerator.createFromText(LanguageLevel.forElement(elementInUnpack), PyExpressionStatement.class, unpacked);
    PyExpression newElement = expressionStatement.getExpression();

    elementTpReplace.replace(newElement);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    var languageLevel = LanguageLevel.forElement(element);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    if (languageLevel.isAtLeast(LanguageLevel.PYTHON311)) {
      String starred = "*" + element.getText();
      PyExpressionStatement expressionStatement = elementGenerator.createFromText(languageLevel, PyExpressionStatement.class, starred);
      PyExpression newElement = expressionStatement.getExpression();
      element.replace(newElement);
    }
    else {
      replaceToTypingExtensionsUnpack(element, element, element.getContainingFile(), project);
    }
  }
}
