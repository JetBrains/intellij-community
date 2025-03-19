package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElementVisitor;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

public abstract class LombokJavaInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public final @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    Module module = ModuleUtilCore.findModuleForFile(holder.getFile());
    if (!LombokLibraryUtil.hasLombokClasses(module)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return createVisitor(holder, isOnTheFly);
  }

  protected abstract @NotNull PsiElementVisitor createVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly);
}
