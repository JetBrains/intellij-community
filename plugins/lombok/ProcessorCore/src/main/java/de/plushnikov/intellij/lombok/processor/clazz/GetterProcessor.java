package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.*;
import de.plushnikov.intellij.lombok.processor.field.GetterFieldProcessor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class GetterProcessor extends AbstractLombokClassProcessor {

  public static final String CLASS_NAME = Getter.class.getName();
  private final GetterFieldProcessor fieldProcessor = new GetterFieldProcessor();

  public GetterProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> boolean process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    boolean result = false;
    for (PsiField psiField : psiClass.getFields()) {
      boolean createSetter = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        createSetter = !modifierList.hasModifierProperty(PsiModifier.STATIC);
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
