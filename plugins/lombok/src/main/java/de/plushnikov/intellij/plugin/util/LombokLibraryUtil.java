package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

public class LombokLibraryUtil {

  private static final String LOMBOK_PACKAGE = "lombok.experimental";

  public static boolean hasLombokLibrary(@NotNull Project project) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(LOMBOK_PACKAGE);
      return new CachedValueProvider.Result<>(aPackage, ProjectRootManager.getInstance(project));
    }) != null;
  }
}
