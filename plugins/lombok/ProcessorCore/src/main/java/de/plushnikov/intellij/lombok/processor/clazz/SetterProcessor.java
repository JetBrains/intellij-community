package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.*;
import de.plushnikov.intellij.lombok.processor.field.SetterFieldProcessor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

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

  public <Psi extends PsiElement> boolean process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    boolean result = false;
    for (PsiField psiField : psiClass.getFields()) {
      boolean createSetter = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        createSetter = !modifierList.hasModifierProperty(PsiModifier.FINAL);
        createSetter &= !modifierList.hasModifierProperty(PsiModifier.STATIC);
        createSetter &= !hasFieldProcessorAnnotation(modifierList);
      }
      if (createSetter)
        fieldProcessor.process(psiField, psiAnnotation, target);
    }

    return result;
  }

  private boolean hasFieldProcessorAnnotation(PsiModifierList modifierList) {
    boolean hasSetterAnnotation = false;
    for (PsiAnnotation fieldAnnotation : modifierList.getAnnotations()) {
      String qualifiedName = fieldAnnotation.getQualifiedName();
      hasSetterAnnotation |= fieldProcessor.acceptAnnotation(qualifiedName, PsiMethod.class);
    }

    return hasSetterAnnotation;
  }

}
