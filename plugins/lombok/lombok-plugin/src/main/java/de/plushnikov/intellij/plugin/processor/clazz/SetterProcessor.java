package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Setter lombok annotation on a class
 * Creates setter methods for fields of this class
 *
 * @author Plushnikov Michail
 */
public class SetterProcessor extends AbstractClassProcessor {

  private final SetterFieldProcessor fieldProcessor = new SetterFieldProcessor();

  public SetterProcessor() {
    this(Setter.class, PsiMethod.class);
  }

  protected SetterProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  protected SetterFieldProcessor getFieldProcessor() {
    return fieldProcessor;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRightType(psiAnnotation, psiClass, builder) && validateVisibility(psiAnnotation);
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("'@%s' is only supported on a class or field type", psiAnnotation.getQualifiedName());
      result = false;
    }
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.addAll(createFieldSetters(psiClass, methodVisibility));
    }
  }

  public Collection<PsiMethod> createFieldSetters(@NotNull PsiClass psiClass, @NotNull String methodModifier) {
    Collection<PsiMethod> result = new ArrayList<PsiMethod>();
    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);

    for (PsiField psiField : psiClass.getFields()) {
      boolean createSetter = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip final fields.
        createSetter = !modifierList.hasModifierProperty(PsiModifier.FINAL);
        //Skip static fields.
        createSetter &= !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields having Setter annotation already
        createSetter &= !hasFieldProcessorAnnotation(modifierList);
        //Skip fields that start with $
        createSetter &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        //Skip fields if a method with same name already exists
        final Collection<String> methodNames = getFieldProcessor().getAllSetterNames(psiField, PsiType.BOOLEAN.equals(psiField.getType()));
        for (String methodName : methodNames) {
          createSetter &= !PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 1);
        }
      }
      if (createSetter) {
        result.add(fieldProcessor.createSetterMethod(psiField, methodModifier));
      }
    }
    return result;
  }

  private boolean hasFieldProcessorAnnotation(PsiModifierList modifierList) {
    boolean hasSetterAnnotation = false;
    for (PsiAnnotation fieldAnnotation : modifierList.getAnnotations()) {
      hasSetterAnnotation |= getFieldProcessor().acceptAnnotation(fieldAnnotation, PsiMethod.class);
    }
    return hasSetterAnnotation;
  }

}
