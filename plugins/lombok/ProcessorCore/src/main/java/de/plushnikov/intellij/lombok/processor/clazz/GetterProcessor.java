package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
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

  public <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    for (PsiField psiField : psiClass.getFields()) {
      boolean createSetter = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        createSetter = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        createSetter &= !hasFieldProcessorAnnotation(modifierList);
      }
      if (createSetter) {
        fieldProcessor.process(psiField, classMethods, psiAnnotation, target);
      }
    }
  }

  private boolean hasFieldProcessorAnnotation(PsiModifierList modifierList) {
    boolean hasSetterAnnotation = false;
    for (PsiAnnotation fieldAnnotation : modifierList.getAnnotations()) {
      hasSetterAnnotation |= fieldProcessor.acceptAnnotation(fieldAnnotation, PsiMethod.class);
    }
    return hasSetterAnnotation;
  }

}
