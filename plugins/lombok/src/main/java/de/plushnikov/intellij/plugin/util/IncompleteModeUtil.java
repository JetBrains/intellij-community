package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public final class IncompleteModeUtil {
  private IncompleteModeUtil() {
  }

  public static boolean isIncompleteMode(@NotNull PsiElement context) {
    if (!(context.getContainingFile() instanceof PsiJavaFile)) return false;
    return isIncompleteMode(context.getProject());
  }

  public static boolean isIncompleteMode(@NotNull Project project) {
    return Registry.is("lombok.incomplete.mode.enabled", false) &&
           !project.getService(IncompleteDependenciesService.class).getState().isComplete();
  }
}
