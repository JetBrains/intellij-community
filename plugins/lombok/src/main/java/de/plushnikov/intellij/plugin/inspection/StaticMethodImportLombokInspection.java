package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.quickfix.ExpandStaticImportFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public final class StaticMethodImportLombokInspection extends LombokJavaInspectionBase {

  @Override
  protected @NotNull PsiElementVisitor createVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return new LombokElementVisitor(holder);
  }

  private static class LombokElementVisitor extends JavaElementVisitor {
    private final ProblemsHolder holder;

    LombokElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement reference) {
      final PsiElement resolvedElement = reference.resolve();
      if (resolvedElement instanceof LombokLightMethodBuilder lombokLightMethodBuilder &&
          lombokLightMethodBuilder.hasModifierProperty(PsiModifier.STATIC)) {
        holder.registerProblem(reference, LombokBundle.message("inspection.static.method.import.error"),
                               ProblemHighlightType.ERROR, new ExpandStaticImportFix());
      }
    }
  }
}
