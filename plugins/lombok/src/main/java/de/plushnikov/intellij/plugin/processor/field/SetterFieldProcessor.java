package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Setter lombok annotation on a field
 * Creates setter method for this field
 *
 * @author Plushnikov Michail
 */
public class SetterFieldProcessor extends AbstractFieldProcessor {

  public SetterFieldProcessor() {
    super(PsiMethod.class, Setter.class);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    final PsiClass psiClass = psiField.getContainingClass();
    if (methodVisibility != null && psiClass != null) {
      target.add(createSetterMethod(psiField, psiClass, methodVisibility));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result;
    result = validateFinalModifier(psiAnnotation, psiField, builder);
    if (result) {
      result = validateVisibility(psiAnnotation);
      if (result) {
        result = validateExistingMethods(psiField, builder);
        if (result) {
          result = validateAccessorPrefix(psiField, builder);
        }
      }
    }
    return result;
  }

  private boolean validateFinalModifier(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiField.hasModifierProperty(PsiModifier.FINAL) && null != LombokProcessorUtil.getMethodModifier(psiAnnotation)) {
      builder.addWarning("Not generating setter for this field: Setters cannot be generated for final fields.",
        PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, false, false));
      result = false;
    }
    return result;
  }

  private boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  private boolean validateExistingMethods(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
      filterToleratedElements(classMethods);

      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
      final Collection<String> methodNames = getAllSetterNames(psiField, isBoolean);

      for (String methodName : methodNames) {
        if (PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 1)) {
          final String setterMethodName = getSetterName(psiField, isBoolean);

          builder.addWarning("Not generated '%s'(): A method with similar name '%s' already exists", setterMethodName, methodName);
          result = false;
        }
      }
    }
    return result;
  }

  private boolean validateAccessorPrefix(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (AccessorsInfo.build(psiField).isPrefixUnDefinedOrNotStartsWith(psiField.getName())) {
      builder.addWarning("Not generating setter for this field: It does not fit your @Accessors prefix list.");
      result = false;
    }
    return result;
  }

  public Collection<String> getAllSetterNames(@NotNull PsiField psiField, boolean isBoolean) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
    return LombokUtils.toAllSetterNames(accessorsInfo, psiField.getName(), isBoolean);
  }

  private String getSetterName(@NotNull PsiField psiField, boolean isBoolean) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);

    return LombokUtils.toSetterName(accessorsInfo, psiField.getName(), isBoolean);
  }

  @NotNull
  public PsiMethod createSetterMethod(@NotNull PsiField psiField, @NotNull PsiClass psiClass, @NotNull String methodModifier) {
    final String fieldName = psiField.getName();
    final PsiType psiFieldType = psiField.getType();
    final PsiAnnotation setterAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, Setter.class);

    final String methodName = getSetterName(psiField, PsiType.BOOLEAN.equals(psiFieldType));

    PsiType returnType = getReturnType(psiField);
    LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiField.getManager(), methodName)
      .withMethodReturnType(returnType)
      .withContainingClass(psiClass)
      .withParameter(fieldName, psiFieldType)
      .withNavigationElement(psiField);
    if (StringUtil.isNotEmpty(methodModifier)) {
      methodBuilder.withModifier(methodModifier);
    }
    boolean isStatic = psiField.hasModifierProperty(PsiModifier.STATIC);
    if (isStatic) {
      methodBuilder.withModifier(PsiModifier.STATIC);
    }

    PsiParameter methodParameter = methodBuilder.getParameterList().getParameters()[0];
    PsiModifierList methodParameterModifierList = methodParameter.getModifierList();
    if (null != methodParameterModifierList) {
      final Collection<String> annotationsToCopy = PsiAnnotationUtil.collectAnnotationsToCopy(psiField,
        LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN);
      for (String annotationFQN : annotationsToCopy) {
        methodParameterModifierList.addAnnotation(annotationFQN);
      }
      addOnXAnnotations(setterAnnotation, methodParameterModifierList, "onParam");
    }

    final String codeBlockText = createCodeBlockText(psiField, psiClass, returnType, isStatic, methodParameter);
    methodBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(codeBlockText, methodBuilder));

    PsiModifierList methodModifierList = methodBuilder.getModifierList();
    copyAnnotations(psiField, methodModifierList, LombokUtils.DEPRECATED_PATTERN);
    addOnXAnnotations(setterAnnotation, methodModifierList, "onMethod");

    return methodBuilder;
  }

  @NotNull
  private String createCodeBlockText(@NotNull PsiField psiField, @NotNull PsiClass psiClass, PsiType returnType, boolean isStatic, PsiParameter methodParameter) {
    final String blockText;
    final String thisOrClass = isStatic ? psiClass.getName() : "this";
    blockText = String.format("%s.%s = %s; ", thisOrClass, psiField.getName(), methodParameter.getName());

    String codeBlockText = blockText;
    if (!isStatic && !PsiType.VOID.equals(returnType)) {
      codeBlockText += "return this;";
    }

    return codeBlockText;
  }

  private PsiType getReturnType(@NotNull PsiField psiField) {
    PsiType result = PsiType.VOID;
    if (!psiField.hasModifierProperty(PsiModifier.STATIC) && AccessorsInfo.build(psiField).isChain()) {
      final PsiClass fieldClass = psiField.getContainingClass();
      if (null != fieldClass) {
        result = PsiClassUtil.getTypeWithGenerics(fieldClass);
      }
    }
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.WRITE;
  }
}
