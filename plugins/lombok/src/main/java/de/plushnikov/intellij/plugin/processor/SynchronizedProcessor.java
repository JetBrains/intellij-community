package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.problem.ProblemValidationSink;
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
    final ProblemValidationSink problemBuilder = new ProblemValidationSink();

    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiAnnotation, PsiMethod.class);
    if (null != psiMethod) {
      final PsiClass containingClass = psiMethod.getContainingClass();

      if (null != containingClass) {
        if (containingClass.isAnnotationType() || containingClass.isInterface() || containingClass.isRecord()) {
          problemBuilder.addErrorMessage("inspection.message.synchronized.legal.only.on.methods.in.classes.enums");
        }
        else {
          if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            problemBuilder.addErrorMessage("inspection.message.synchronized.legal.only.on.concrete.methods")
              .withLocalQuickFixes(PsiQuickFixFactory.createModifierListFix(psiMethod, PsiModifier.ABSTRACT, false, false));
          }
          else {
            validateReferencedField(problemBuilder, psiAnnotation, psiMethod, containingClass);
          }
        }
      }
    }

    return problemBuilder.getProblems();
  }

  private static void validateReferencedField(@NotNull ProblemSink problemNewBuilder, @NotNull PsiAnnotation psiAnnotation,
                                              @NotNull PsiMethod psiMethod, @NotNull PsiClass containingClass) {
    @NlsSafe final String lockFieldName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "value", "");
    if (StringUtil.isNotEmpty(lockFieldName)) {
      final boolean isStatic = psiMethod.hasModifierProperty(PsiModifier.STATIC);

      final PsiField lockField = containingClass.findFieldByName(lockFieldName, true);
      if (null != lockField) {
        if (isStatic && !lockField.hasModifierProperty(PsiModifier.STATIC)) {
          problemNewBuilder.addErrorMessage("inspection.message.synchronized.field.is.not.static", lockFieldName)
            .withLocalQuickFixes(PsiQuickFixFactory.createModifierListFix(lockField, PsiModifier.STATIC, true, false));
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
        problemNewBuilder.addErrorMessage("inspection.message.field.s.does.not.exist", lockFieldName)
          .withLocalQuickFixes(
            PsiQuickFixFactory.createNewFieldFix(containingClass, lockFieldName, javaLangObjectType, "new Object()", modifiers));
      }
    }
  }
}
