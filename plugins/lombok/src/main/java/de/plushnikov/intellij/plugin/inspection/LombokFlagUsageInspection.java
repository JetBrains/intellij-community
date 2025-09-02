package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.intention.valvar.from.ReplaceValWithExplicitTypeIntentionAction;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LombokFlagUsageInspection extends LombokJavaInspectionBase {
  @Override
  protected @NotNull PsiElementVisitor createVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return new LombokFlagUsageElementVisitor(holder);
  }

  private static class LombokFlagUsageElementVisitor extends JavaElementVisitor {
    private final ProblemsHolder holder;

    LombokFlagUsageElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitClass(@NotNull PsiClass psiClass) {
      for (PsiAnnotation psiAnnotation : psiClass.getAnnotations()) {
        visitAnnotatedElement(psiAnnotation);

        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.DATA, ConfigKey.DATA_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.VALUE, ConfigKey.VALUE_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.EQUALS_AND_HASHCODE, ConfigKey.EQUALS_AND_HASHCODE_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.TO_STRING, ConfigKey.TOSTRING_FLAG_USAGE);

        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.SUPER_BUILDER, ConfigKey.SUPER_BUILDER_FLAG_USAGE,
                                    ConfigKey.EXPERIMENTAL_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.HELPER, ConfigKey.HELPER_FLAG_USAGE,
                                    ConfigKey.EXPERIMENTAL_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.UTILITY_CLASS, ConfigKey.UTILITY_CLASS_FLAG_USAGE,
                                    ConfigKey.EXPERIMENTAL_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.STANDARD_EXCEPTION, ConfigKey.STANDARD_EXCEPTION_FLAG_USAGE,
                                    ConfigKey.EXPERIMENTAL_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.EXTENSION_METHOD, ConfigKey.EXTENSION_METHOD_FLAG_USAGE,
                                    ConfigKey.EXPERIMENTAL_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.FIELD_DEFAULTS, ConfigKey.FIELD_DEFAULTS_FLAG_USAGE,
                                    ConfigKey.EXPERIMENTAL_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.FIELD_NAME_CONSTANTS, ConfigKey.FIELD_NAME_CONSTANT_FLAG_USAGE,
                                    ConfigKey.EXPERIMENTAL_FLAG_USAGE);

        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.COMMONS_LOG, ConfigKey.LOG_APACHE_COMMONS_FLAG_USAGE,
                                    ConfigKey.LOG_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.CUSTOM_LOG, ConfigKey.LOG_CUSTOM_USAGE, ConfigKey.LOG_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.FLOGGER, ConfigKey.LOG_FLOGGER_FLAG_USAGE, ConfigKey.LOG_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.JAVA_LOG, ConfigKey.LOG_JAVA_UTIL_LOGGING_FLAG_USAGE,
                                    ConfigKey.LOG_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.JBOSS_LOG, ConfigKey.LOG_JBOSSLOG_FLAG_USAGE, ConfigKey.LOG_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.LOG_4_J, ConfigKey.LOG_LOG4J_FLAG_USAGE, ConfigKey.LOG_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.LOG_4_J_2, ConfigKey.LOG_LOG4J2_FLAG_USAGE, ConfigKey.LOG_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.SLF_4_J, ConfigKey.LOG_SLF4J_FLAG_USAGE, ConfigKey.LOG_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.XSLF_4_J, ConfigKey.LOG_XSLF4J_FLAG_USAGE, ConfigKey.LOG_FLAG_USAGE);

        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.ALL_ARGS_CONSTRUCTOR,
                                    ConfigKey.ALL_ARGS_CONSTRUCTOR_FLAG_USAGE,
                                    ConfigKey.ANY_CONSTRUCTOR_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.NO_ARGS_CONSTRUCTOR,
                                    ConfigKey.NO_ARGS_CONSTRUCTOR_FLAG_USAGE,
                                    ConfigKey.ANY_CONSTRUCTOR_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR,
                                    ConfigKey.REQUIRED_ARGS_CONSTRUCTOR_FLAG_USAGE,
                                    ConfigKey.ANY_CONSTRUCTOR_FLAG_USAGE);

        checkOnXAnnotationAndFlagUsage(psiAnnotation, "onConstructor", LombokClassNames.NO_ARGS_CONSTRUCTOR,
                                       LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR, LombokClassNames.ALL_ARGS_CONSTRUCTOR);
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod psiMethod) {
      for (PsiAnnotation psiAnnotation : psiMethod.getAnnotations()) {
        visitAnnotatedElement(psiAnnotation);

        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.SNEAKY_THROWS, ConfigKey.SNEAKY_THROWS_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.SYNCHRONIZED, ConfigKey.SYNCHRONIZED_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.TOLERATE, ConfigKey.EXPERIMENTAL_FLAG_USAGE);
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.LOCKED, ConfigKey.LOCKED_FLAG_USAGE);
      }
    }

    @Override
    public void visitField(@NotNull PsiField psiField) {
      for (PsiAnnotation psiAnnotation : psiField.getAnnotations()) {
        visitAnnotatedElement(psiAnnotation);
      }
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      for (PsiAnnotation psiAnnotation : variable.getAnnotations()) {
        checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.CLEANUP, ConfigKey.CLEANUP_FLAG_USAGE);
      }

      checkValVarUsage(variable);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      checkValVarUsage(parameter);
    }

    private void checkValVarUsage(@NotNull PsiVariable variable) {
      PsiClass containingClass = PsiTreeUtil.getParentOfType(variable, PsiClass.class);
      if (containingClass != null) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement != null) {
          if (ValProcessor.isVal(variable)) {
            checkFlagUsage(typeElement, LombokClassNames.VAL, containingClass, ConfigKey.VAL_FLAG_USAGE,
                           LocalQuickFix.from(new ReplaceValWithExplicitTypeIntentionAction()));
          }
          if (ValProcessor.isVar(variable)) {
            checkFlagUsage(typeElement, LombokClassNames.VAR, containingClass, ConfigKey.VAR_FLAG_USAGE,
                           LocalQuickFix.from(new ReplaceValWithExplicitTypeIntentionAction()));
          }
        }
      }
    }

    private void visitAnnotatedElement(@NotNull PsiAnnotation psiAnnotation) {
      checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.ACCESSORS, ConfigKey.ACCESSORS_FLAG_USAGE,
                                  ConfigKey.EXPERIMENTAL_FLAG_USAGE);

      checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.WITH_BY, ConfigKey.WITHBY_FLAG_USAGE);

      checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.BUILDER, ConfigKey.BUILDER_FLAG_USAGE);

      checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.DELEGATE, ConfigKey.DELEGATE_FLAG_USAGE);
      checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.GETTER, ConfigKey.GETTER_FLAG_USAGE);
      checkLazyGetterAnnotationAndFlagUsage(psiAnnotation);

      checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.JACKSONIZED, ConfigKey.JACKSONIZED_FLAG_USAGE);

      checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.NON_NULL, ConfigKey.NONNULL_FLAG_USAGE);
      checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.SETTER, ConfigKey.SETTER_FLAG_USAGE);

      checkAnnotationAndFlagUsage(psiAnnotation, LombokClassNames.WITH, ConfigKey.WITH_FLAG_USAGE);

      checkOnXAnnotationAndFlagUsage(psiAnnotation, "onParam", LombokClassNames.SETTER);
      checkOnXAnnotationAndFlagUsage(psiAnnotation, "onMethod", LombokClassNames.GETTER, LombokClassNames.SETTER);
    }

    private void checkAnnotationAndFlagUsage(@NotNull PsiAnnotation annotation,
                                             @NotNull @NonNls String lombokAnnotationQualifiedName,
                                             ConfigKey @NotNull ... flagUsageKeys) {
      if (!annotation.hasQualifiedName(lombokAnnotationQualifiedName)) {
        return;
      }

      PsiClass parentOfAnnotation = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
      if (parentOfAnnotation == null) {
        return;
      }

      for (ConfigKey flagUsageKey : flagUsageKeys) {
        checkFlagUsageKey(annotation, StringUtil.getShortName(lombokAnnotationQualifiedName), parentOfAnnotation, flagUsageKey);
      }
    }

    private void checkFlagUsageKey(@NotNull PsiAnnotation annotation,
                                   @NotNull String featureName,
                                   PsiClass parentOfAnnotation,
                                   ConfigKey flagUsageKey) {
      checkFlagUsage(annotation, featureName, parentOfAnnotation, flagUsageKey,
                     new RemoveAnnotationQuickFix(annotation, null));
    }

    private void checkFlagUsage(@NotNull PsiElement problemElement,
                                @NotNull String featureName,
                                PsiClass parentOfAnnotation,
                                ConfigKey flagUsageKey,
                                @NotNull LocalQuickFix @Nullable ... fixes) {
      final String configProperty = ConfigDiscovery.getInstance().getStringLombokConfigProperty(flagUsageKey, parentOfAnnotation);
      if (!configProperty.isEmpty() && !configProperty.equalsIgnoreCase("ALLOW")) {
        final String msg =
          LombokBundle.message("inspection.message.use.flagged.lombok.annotation", featureName);

        if (configProperty.equalsIgnoreCase("ERROR")) {
          holder.registerProblem(problemElement, msg, ProblemHighlightType.ERROR, fixes);
        }
        else if (configProperty.equalsIgnoreCase("WARNING")) {
          holder.registerProblem(problemElement, msg, ProblemHighlightType.WARNING, fixes);
        }
      }
    }

    private void checkLazyGetterAnnotationAndFlagUsage(@NotNull PsiAnnotation psiAnnotation) {
      if (!psiAnnotation.hasQualifiedName(LombokClassNames.GETTER)) {
        return;
      }
      if (!PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "lazy", false)) {
        return;
      }

      PsiClass parentOfAnnotation = PsiTreeUtil.getParentOfType(psiAnnotation, PsiClass.class);
      if (parentOfAnnotation == null) {
        return;
      }

      checkFlagUsageKey(psiAnnotation, "Getter(lazy=true)", parentOfAnnotation, ConfigKey.GETTER_LAZY_FLAG_USAGE);
    }

    private void checkOnXAnnotationAndFlagUsage(@NotNull PsiAnnotation psiAnnotation,
                                                @NotNull String parameterName, String @NotNull ... qualifiedNames) {

      for (String qualifiedName : qualifiedNames) {
        if (psiAnnotation.hasQualifiedName(qualifiedName)) {
          Iterable<String> annotationsToAdd = LombokProcessorUtil.getOnX(psiAnnotation, parameterName);
          if (annotationsToAdd.iterator().hasNext()) {

            PsiClass parentOfAnnotation = PsiTreeUtil.getParentOfType(psiAnnotation, PsiClass.class);
            if (parentOfAnnotation == null) {
              return;
            }

            checkFlagUsageKey(psiAnnotation, String.format("%s(%s=...)", qualifiedName, parameterName), parentOfAnnotation,
                              ConfigKey.ONX_FLAG_USAGE);
          }
          break;
        }
      }
    }
  }
}
