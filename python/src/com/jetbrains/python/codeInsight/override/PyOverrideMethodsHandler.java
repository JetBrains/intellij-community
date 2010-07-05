package com.jetbrains.python.codeInsight.override;

import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class PyOverrideMethodsHandler implements LanguageCodeInsightActionHandler {
  public boolean isValidFor(Editor editor, PsiFile file) {
    return (file instanceof PyFile) && (PyOverrideImplementUtil.getContextClass(file.getProject(), editor, file) != null);
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PyClass aClass = PyOverrideImplementUtil.getContextClass(project, editor, file);
    if (aClass != null) {
      PyOverrideImplementUtil.chooseAndOverrideMethods(project, editor, aClass);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
