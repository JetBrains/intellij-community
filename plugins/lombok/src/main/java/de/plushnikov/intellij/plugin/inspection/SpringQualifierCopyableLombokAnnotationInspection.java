package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Plushnikov Michail
 */
public final class SpringQualifierCopyableLombokAnnotationInspection extends LombokJavaInspectionBase {

  private static final String SPRING_QUALIFIER_FQN = "org.springframework.beans.factory.annotation.Qualifier";

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
      if (annotation.hasQualifiedName(SPRING_QUALIFIER_FQN)) {

        PsiAnnotationOwner annotationOwner = annotation.getOwner();
        if (annotationOwner instanceof PsiModifierList) {
          PsiElement annotationOwnerParent = ((PsiModifierList)annotationOwner).getParent();
          if (annotationOwnerParent instanceof PsiField) {
            PsiClass psiClass = ((PsiField)annotationOwnerParent).getContainingClass();
            if (psiClass != null && PsiAnnotationSearchUtil.isAnnotatedWith(psiClass,
                                                                            LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR,
                                                                            LombokClassNames.ALL_ARGS_CONSTRUCTOR)) {
              Collection<String> configuredCopyableAnnotations =
                ConfigDiscovery.getInstance().getMultipleValueLombokConfigProperty(ConfigKey.COPYABLE_ANNOTATIONS, psiClass);

              if (!configuredCopyableAnnotations.contains(SPRING_QUALIFIER_FQN)) {
                holder.registerProblem(annotation,
                                       LombokBundle.message("inspection.message.annotation.not.lombok.copyable",
                                                            SPRING_QUALIFIER_FQN));
              }
            }
          }
        }
      }
    }
  }
}
