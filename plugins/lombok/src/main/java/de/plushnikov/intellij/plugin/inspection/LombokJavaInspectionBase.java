package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

public abstract class LombokJavaInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public final @NotNull
  PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!LombokLibraryUtil.hasLombokLibrary(holder.getProject())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return createVisitor(holder, isOnTheFly);
  }

  @NotNull
  protected abstract PsiElementVisitor createVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly);
}
