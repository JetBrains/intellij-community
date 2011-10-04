package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.lombok.LombokConstants;
import de.plushnikov.intellij.lombok.problem.LombokProblem;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.lombok.problem.ProblemNewBuilder;
import de.plushnikov.intellij.lombok.processor.AbstractLombokProcessor;
import de.plushnikov.intellij.lombok.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Base lombok processor class for class annotations
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokClassProcessor extends AbstractLombokProcessor implements LombokClassProcessor {

  protected AbstractLombokClassProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    super(supportedAnnotation, supportedClass);
  }

  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    Collection<LombokProblem> result = Collections.emptyList();
    // check first for fields, methods and filter it out, because PsiClass is parent of all annotations and will match other parents too
    PsiElement psiElement = PsiTreeUtil.getParentOfType(psiAnnotation, PsiField.class, PsiMethod.class, PsiClass.class);
    if (psiElement instanceof PsiClass) {
      result = new ArrayList<LombokProblem>(1);
      validate(psiAnnotation, (PsiClass) psiElement, new ProblemNewBuilder(result));
    }

    return result;
  }

  protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder);

  public final <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    if (validate(psiAnnotation, psiClass, new ProblemEmptyBuilder())) {
      processIntern(psiClass, psiAnnotation, target);
    }
  }

  protected abstract <Psi extends PsiElement> void processIntern(PsiClass psiClass, PsiAnnotation psiAnnotation, List<Psi> target);

  protected void validateCallSuperParam(PsiAnnotation psiAnnotation, PsiClass psiClass, ProblemBuilder builder, String generatedMethodName) {
    Boolean callSuperProperty = PsiAnnotationUtil.getDeclaredAnnotationValue(psiAnnotation, "callSuper", Boolean.class);
    if (null == callSuperProperty) {
      final PsiClass superClass = psiClass.getSuperClass();
      if (null != superClass && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        builder.addWarning("Generating " + generatedMethodName + " implementation but without a call to superclass, " +
            "even though this class does not extend java.lang.Object." +
            "If this is intentional, add '(callSuper=false)' to your type.",
            PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", "true"),
            PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", "false"));
      }
    }
  }

  protected void validateOfParam(PsiClass psiClass, ProblemBuilder builder, Collection<String> ofProperty) {
    for (String fieldName : ofProperty) {
      if (!StringUtil.isEmptyOrSpaces(fieldName)) {
        PsiField fieldByName = psiClass.findFieldByName(fieldName, false);
        if (null == fieldByName) {
          builder.addWarning(String.format("The field '%s' does not exist", fieldName));//TODO add QuickFix  : remove of param
        }
      }
    }
  }

  protected void validateExcludeParam(PsiClass psiClass, ProblemBuilder builder, Collection<String> excludeProperty) {
    for (String fieldName : excludeProperty) {
      if (!StringUtil.isEmptyOrSpaces(fieldName)) {
        PsiField fieldByName = psiClass.findFieldByName(fieldName, false);
        if (null == fieldByName) {
          builder.addWarning(String.format("The field '%s' does not exist", fieldName));//TODO add QuickFix  : remove exclude param
        } else {
          if (fieldName.startsWith(LombokConstants.LOMBOK_INTERN_FIELD_MARKER) || fieldByName.hasModifierProperty(PsiModifier.STATIC)) {
            builder.addWarning(String.format("The field '%s' would have been excluded anyway", fieldName));//TODO add QuickFix  : remove exclude param
          }
        }
      }
    }
  }
}
