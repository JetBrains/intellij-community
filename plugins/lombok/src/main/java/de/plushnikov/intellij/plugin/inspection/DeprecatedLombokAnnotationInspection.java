package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author Plushnikov Michail
 */
public class DeprecatedLombokAnnotationInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public String getDisplayName() {
    return "Deprecated lombok annotations inspection";
  }

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "DeprecatedLombok";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new LombokElementVisitor(holder);
  }

  private static class LombokElementVisitor extends JavaElementVisitor {

    private final ProblemsHolder holder;

    LombokElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitAnnotation(final PsiAnnotation annotation) {
      checkFor("lombok.experimental.Builder", annotation);
      checkFor("lombok.experimental.Value", annotation);
    }

    private void checkFor(String deprecatedAnnotationFQN, PsiAnnotation psiAnnotation) {
      final String annotationQualifiedName = psiAnnotation.getQualifiedName();
      if (Objects.equals(deprecatedAnnotationFQN, annotationQualifiedName)) {
        final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(psiAnnotation, PsiModifierListOwner.class, false);
        if (null != listOwner) {
          final String newAnnotationFQN = deprecatedAnnotationFQN.replace("experimental.", "");

          holder.registerProblem(psiAnnotation,
            "Lombok annotation '" + deprecatedAnnotationFQN + "' is deprecated and " +
              "not supported by lombok-plugin any more. Use '" + newAnnotationFQN + "' instead.",
            ProblemHighlightType.ERROR,
            new AddAnnotationFix(newAnnotationFQN, listOwner, deprecatedAnnotationFQN));
        }
      }
    }

  }
}
