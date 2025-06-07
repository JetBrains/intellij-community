package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public final class DeprecatedLombokAnnotationInspection extends LombokJavaInspectionBase {

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
    public void visitAnnotation(final @NotNull PsiAnnotation annotation) {
      checkFor("lombok.experimental.Builder", LombokClassNames.BUILDER, annotation);
      checkFor("lombok.experimental.Value", LombokClassNames.VALUE, annotation);
      checkFor("lombok.experimental.Wither", LombokClassNames.WITH, annotation);
    }

    private void checkFor(String deprecatedFQN, String newFQN, PsiAnnotation psiAnnotation) {
      if (psiAnnotation.hasQualifiedName(deprecatedFQN)) {
        final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(psiAnnotation, PsiModifierListOwner.class, false);
        if (null != listOwner) {
          String message = LombokBundle.message("inspection.message.lombok.annotation.deprecated.not.supported", deprecatedFQN, newFQN);
          holder.problem(psiAnnotation, message).highlight(ProblemHighlightType.ERROR)
            .fix(new AddAnnotationModCommandAction(
              newFQN, listOwner, psiAnnotation.getParameterList().getAttributes(), deprecatedFQN))
            .register();
        }
      }
    }
  }
}
