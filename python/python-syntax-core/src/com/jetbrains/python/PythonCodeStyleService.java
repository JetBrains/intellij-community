package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class PythonCodeStyleService {

  public static final Key<Boolean> IMPORT_GROUP_BEGIN = Key.create("com.jetbrains.python.formatter.importGroupBegin");

  public boolean isSpaceAroundEqInKeywordArgument(@NotNull PsiFile file) {
    return false;
  }

  public boolean isTabIndentation(@NotNull PsiFile file) {
    return false;
  }

  public int getIndentSize(@NotNull PsiFile file) {
    return 4;
  }

  public int getTabSize(@NotNull PsiFile file) {
    return 4;
  }

  public boolean isOptimizeImportsSortedByTypeFirst(@NotNull PsiFile file) {
    return false;
  }

  public boolean isOptimizeImportsAlwaysSplitFromImports(@NotNull PsiFile file) {
    return false;
  }

  public boolean isOptimizeImportsCaseSensitiveOrder(@NotNull PsiFile file) {
    return false;
  }

  public boolean isOptimizeImportsSortNamesInFromImports(@NotNull PsiFile file) {
    return false;
  }

  public boolean isOptimizeImportsSortImports(@NotNull PsiFile file) {
    return false;
  }

  public void reparseOpenEditorFiles(@NotNull Project project) {
  }

  public void setSpaceAroundEqInKeywordArgument(@NotNull Project project, boolean enabled) {
  }

  public static PythonCodeStyleService getInstance() {
    return ApplicationManager.getApplication().getService(PythonCodeStyleService.class);
  }
}
