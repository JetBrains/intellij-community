package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.LombokConstants;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.processor.field.GetterFieldProcessor;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.Getter;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Getter lombok annotation on a class
 * Creates getter methods for fields of this class
 *
 * @author Plushnikov Michail
 */
public class GetterProcessor extends AbstractLombokClassProcessor {

  public static final String CLASS_NAME = Getter.class.getName();
  private final GetterFieldProcessor fieldProcessor = new GetterFieldProcessor();

  public GetterProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final boolean result = validateAnnotationOnRigthType(psiClass, builder) && validateVisibility(psiAnnotation);

    final String lazyAsString = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "lazy");
    if (Boolean.valueOf(lazyAsString)) {
      builder.addWarning("'lazy' is not supported for @Getter on a type");
    }

    return result;
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("'@Getter' is only supported on a class, enum or field type");
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
      target.addAll((Collection<? extends Psi>) createFieldGetters(psiClass, methodVisibility));
    }
  }

  @NotNull
  public Collection<PsiMethod> createFieldGetters(@NotNull PsiClass psiClass, @NotNull String methodVisibility) {
    Collection<PsiMethod> result = new ArrayList<PsiMethod>();
    final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);

    for (PsiField psiField : psiClass.getFields()) {
      boolean createGetter = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        createGetter = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields having Getter annotation already
        createGetter &= !hasFieldProcessorAnnotation(modifierList);
        //Skip fields that start with $
        createGetter &= !psiField.getName().startsWith(LombokConstants.LOMBOK_INTERN_FIELD_MARKER);
        //Skip fields if a method with same name already exists
        final Collection<String> methodNames = TransformationsUtil.toAllGetterNames(psiField.getName(), PsiType.BOOLEAN.equals(psiField.getType()));
        createGetter &= !PsiMethodUtil.hasMethodByName(classMethods, methodNames);
      }
      if (createGetter) {
        result.add(fieldProcessor.createGetterMethod(psiField, methodVisibility));
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
