package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.Setter;
import lombok.core.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Setter lombok annotation on a field
 * Creates setter method for this field
 *
 * @author Plushnikov Michail
 */
public class SetterFieldProcessor extends AbstractLombokFieldProcessor {

  public static final String CLASS_NAME = Setter.class.getName();

  public SetterFieldProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.add((Psi) createSetterMethod(psiField, methodVisibility));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result;
    result = validateFinalModifier(psiField, builder);
    if (result) {
      result = validateVisibility(psiAnnotation);
      if (result) {
        result = validateExistingMethods(psiField, builder);
      }
    }
    return result;
  }

  protected boolean validateFinalModifier(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiField.hasModifierProperty(PsiModifier.FINAL)) {
      builder.addError("'@Setter' on final field is not allowed",
          PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, false, false));
      result = false;
    }
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibity = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibity;
  }

  protected boolean validateExistingMethods(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
      final Collection<String> methodNames = TransformationsUtil.toAllSetterNames(psiField.getName(), isBoolean);

      for (String methodName : methodNames) {
        if (PsiMethodUtil.hasMethodByName(classMethods, methodName)) {
          final String setterMethodName = TransformationsUtil.toSetterName(psiField.getName(), isBoolean);

          builder.addWarning(String.format("Not generated '%s'(): A method with similar name '%s' already exists", setterMethodName, methodName));
          result = false;
        }
      }
    }
    return result;
  }

  @NotNull
  public PsiMethod createSetterMethod(@NotNull PsiField psiField, @Modifier @NotNull String methodModifier) {
    final String fieldName = psiField.getName();
    final PsiType psiFieldType = psiField.getType();
    final String methodName = TransformationsUtil.toSetterName(fieldName, PsiType.BOOLEAN.equals(psiFieldType));

//      final Collection<String> annotationsToCopy = PsiAnnotationUtil.collectAnnotationsToCopy(psiField);
//      final String annotationsString = PsiAnnotationUtil.buildAnnotationsString(annotationsToCopy);
//    //TODO adapt annotations

    PsiClass psiClass = psiField.getContainingClass();
    assert psiClass != null;

    UserMapKeys.addWriteUsageFor(psiField);

    //return PsiMethodUtil.createMethod(psiClass, builder.toString(), psiField);
    //PsiMethod method = PropertyUtil.generateSetterPrototype(psiField);
    LombokLightMethodBuilder method = new LombokLightMethodBuilder(psiField.getManager(), methodName)
        .setMethodReturnType(PsiType.VOID)
        .setContainingClass(psiClass)
        .addParameter(fieldName, psiFieldType)
        .setNavigationElement(psiField);
    if (StringUtil.isNotEmpty(methodModifier)) {
      method.addModifier(methodModifier);
    }
    if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
      method.addModifier(PsiModifier.STATIC);
    }
    return method;

  }

}
