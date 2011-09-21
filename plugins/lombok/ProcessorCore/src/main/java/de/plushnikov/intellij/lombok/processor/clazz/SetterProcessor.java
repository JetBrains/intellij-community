package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.processor.field.SetterFieldProcessor;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.Setter;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class SetterProcessor extends AbstractLombokClassProcessor {

  private static final String CLASS_NAME = Setter.class.getName();
  private final SetterFieldProcessor fieldProcessor = new SetterFieldProcessor();

  public SetterProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    // TODO if (lazy) addError("'lazy' is not supported for @Getter on a type.");
    // TODO Warning("'lazy' does not work with AccessLevel.NONE."
    // TODO "'lazy' requires the field to be private and final.
    // TODO "'lazy' requires field initialization."
    return validateAnnotationOnRigthType(psiClass, builder) && validateVisibility(psiAnnotation);
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("@Setter is only supported on a class or field type");
      result = false;
    }
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodVisibility(psiAnnotation);
    return null != methodVisibility;
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodVisibility(psiAnnotation);
    if (methodVisibility != null) {
      target.addAll((Collection<? extends Psi>) createFieldSetters(psiClass, methodVisibility));
    }
  }

  public Collection<PsiMethod> createFieldSetters(PsiClass psiClass, String methodVisibility) {
    Collection<PsiMethod> result = new ArrayList<PsiMethod>();
    final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);

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
        createSetter &= !psiField.getName().startsWith("$");
        //Skip fields if a method with same name already exists
        final Collection<String> methodNames = TransformationsUtil.toAllSetterNames(psiField.getName(), PsiType.BOOLEAN.equals(psiField.getType()));
        createSetter &= !PsiMethodUtil.hasMethodByName(classMethods, methodNames);
      }
      if (createSetter) {
        result.add(fieldProcessor.createSetterMethod(psiField, methodVisibility));
      }
    }
    return result;
  }

  private boolean hasFieldProcessorAnnotation(PsiModifierList modifierList) {
    boolean hasSetterAnnotation = false;
    for (PsiAnnotation fieldAnnotation : modifierList.getAnnotations()) {
      hasSetterAnnotation |= fieldProcessor.acceptAnnotation(fieldAnnotation, PsiMethod.class);
    }
    return hasSetterAnnotation;
  }

}
