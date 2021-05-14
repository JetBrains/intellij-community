package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author Plushnikov Michail
 */
public class SpringQualifierCopyableLombokAnnotationInspection extends LombokJavaInspectionBase {

  public static final String SPRINGFRAMEWORK_BEANS_FACTORY_ANNOTATION_QUALIFIER = "org.springframework.beans.factory.annotation.Qualifier";

  @NotNull
  @Override
  protected PsiElementVisitor createVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new LombokElementVisitor(holder);
  }

  private static class LombokElementVisitor extends JavaElementVisitor {
    private final ProblemsHolder holder;

    LombokElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitAnnotation(final PsiAnnotation annotation) {
      if (Objects.equals(PsiAnnotationSearchUtil.getSimpleNameOf(annotation), StringUtil.getShortName(
        SPRINGFRAMEWORK_BEANS_FACTORY_ANNOTATION_QUALIFIER)) &&
          Objects.equals(annotation.getQualifiedName(), SPRINGFRAMEWORK_BEANS_FACTORY_ANNOTATION_QUALIFIER)) {

        PsiField psiField = PsiTreeUtil.getParentOfType(annotation, PsiField.class);
        if (psiField != null) {
          PsiClass psiClass = PsiTreeUtil.getParentOfType(psiField, PsiClass.class);
          if (psiClass != null) {
            if (PsiAnnotationSearchUtil
              .isAnnotatedWith(psiClass, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR, LombokClassNames.ALL_ARGS_CONSTRUCTOR)) {

              String[] configuredCopyableAnnotations =
                ConfigDiscovery.getInstance().getMultipleValueLombokConfigProperty(ConfigKey.COPYABLE_ANNOTATIONS, psiClass);

              if (!Arrays.asList(configuredCopyableAnnotations).contains(SPRINGFRAMEWORK_BEANS_FACTORY_ANNOTATION_QUALIFIER)) {
                holder.registerProblem(annotation,
                                       LombokBundle.message("inspection.message.annotation.not.lombok.copyable",
                                                            SPRINGFRAMEWORK_BEANS_FACTORY_ANNOTATION_QUALIFIER),
                                       ProblemHighlightType.WARNING);
              }
            }
          }
        }
      }
    }
  }
}
