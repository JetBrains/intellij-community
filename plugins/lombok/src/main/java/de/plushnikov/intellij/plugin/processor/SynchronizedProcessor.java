package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemNewBuilder;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Inspect and validate @Synchronized lombok annotation
 *
 * @author Plushnikov Michail
 */
public class SynchronizedProcessor extends AbstractProcessor {

  public SynchronizedProcessor() {
    super(PsiElement.class, LombokClassNames.SYNCHRONIZED);
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    final ProblemNewBuilder problemBuilder = new ProblemNewBuilder();

    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiAnnotation, PsiMethod.class);
    if (null != psiMethod) {
      final PsiClass containingClass = psiMethod.getContainingClass();

      if (null != containingClass) {
        if (containingClass.isAnnotationType() || containingClass.isInterface() || containingClass.isRecord()) {
          problemBuilder.addError(LombokBundle.message("inspection.message.synchronized.legal.only.on.methods.in.classes.enums"));
        }
        else {
          if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            problemBuilder.addError(LombokBundle.message("inspection.message.synchronized.legal.only.on.concrete.methods"),
                                       PsiQuickFixFactory.createModifierListFix(psiMethod, PsiModifier.ABSTRACT, false, false)
            );
          }
          else {
            validateReferencedField(problemBuilder, psiAnnotation, psiMethod, containingClass);
          }
        }
      }
    }

    return problemBuilder.getProblems();
  }

  private static void validateReferencedField(@NotNull ProblemBuilder problemNewBuilder, @NotNull PsiAnnotation psiAnnotation,
                                              @NotNull PsiMethod psiMethod, @NotNull PsiClass containingClass) {
    @NlsSafe final String lockFieldName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "value", "");
    if (StringUtil.isNotEmpty(lockFieldName)) {
      final boolean isStatic = psiMethod.hasModifierProperty(PsiModifier.STATIC);

      final PsiField lockField = containingClass.findFieldByName(lockFieldName, true);
      if (null != lockField) {
        if (isStatic && !lockField.hasModifierProperty(PsiModifier.STATIC)) {
          problemNewBuilder.addError(
            LombokBundle.message("inspection.message.synchronized.field.is.not.static", lockFieldName),
            PsiQuickFixFactory.createModifierListFix(lockField, PsiModifier.STATIC, true, false));
        }
      }
      else {
        final PsiClassType javaLangObjectType =
          PsiType.getJavaLangObject(containingClass.getManager(), containingClass.getResolveScope());

        final String[] modifiers;
        if (isStatic) {
          modifiers = new String[]{PsiModifier.PRIVATE, PsiModifier.FINAL, PsiModifier.STATIC};
        }
        else {
          modifiers = new String[]{PsiModifier.PRIVATE, PsiModifier.FINAL};
        }
        problemNewBuilder.addError(LombokBundle.message("inspection.message.field.s.does.not.exist", lockFieldName),
                                   PsiQuickFixFactory.createNewFieldFix(containingClass, lockFieldName, javaLangObjectType,
                                                                        "new Object()", modifiers));
      }
    }
  }
}
