package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WitherFieldProcessor extends AbstractFieldProcessor {

  private final RequiredArgsConstructorProcessor requiredArgsConstructorProcessor;

  public WitherFieldProcessor(RequiredArgsConstructorProcessor requiredArgsConstructorProcessor) {
    super(PsiMethod.class, Wither.class);
    this.requiredArgsConstructorProcessor = requiredArgsConstructorProcessor;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean valid = validateVisibility(psiAnnotation);
    valid &= validName(psiField, builder);
    valid &= validNonStatic(psiField, builder);
    valid &= validNonFinalInitialized(psiField, builder);
    valid &= validIsWitherUnique(psiField, builder);

    final PsiClass containingClass = psiField.getContainingClass();
    valid &= null != containingClass && (containingClass.hasModifierProperty(PsiModifier.ABSTRACT) || validConstructor(containingClass, builder));

    return valid;
  }

  private boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    String methodModifier = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodModifier != null) {
      AccessorsInfo accessorsInfo = buildAccessorsInfo(psiField);
      PsiMethod method = createWitherMethod(psiField, methodModifier, accessorsInfo);
      if (method != null) {
        target.add(method);
      }
    }
  }

  private boolean validName(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    if (psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER)) {
      builder.addWarning("Not generating wither for this field: Withers cannot be generated for fields starting with $.");
      return false;
    }
    return true;
  }

  private boolean validNonStatic(@NotNull PsiField psiField, @NotNull final ProblemBuilder builder) {
    if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
      builder.addWarning("Not generating wither for this field: Withers cannot be generated for static fields.",
        PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.STATIC, false, false));
      return false;
    }
    return true;
  }

  private boolean validNonFinalInitialized(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    if (psiField.hasModifierProperty(PsiModifier.FINAL) && psiField.getInitializer() != null) {
      builder.addWarning("Not generating wither for this field: Withers cannot be generated for final, initialized fields.",
        PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, false, false));
      return false;
    }
    return true;
  }

  private boolean validIsWitherUnique(@NotNull PsiField psiField, @NotNull final ProblemBuilder builder) {
    final PsiClass fieldContainingClass = psiField.getContainingClass();
    final String psiFieldName = psiField.getName();
    if (psiFieldName != null && fieldContainingClass != null) {
      final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(fieldContainingClass);

      final AccessorsInfo accessorsInfo = buildAccessorsInfo(psiField);
      final Collection<String> possibleWitherNames = LombokUtils.toAllWitherNames(accessorsInfo, psiFieldName, PsiType.BOOLEAN.equals(psiField.getType()));
      for (String witherName : possibleWitherNames) {
        if (PsiMethodUtil.hasSimilarMethod(classMethods, witherName, 1)) {
          builder.addWarning("Not generating %s(): A method with that name already exists", witherName);
          return false;
        }
      }
    }
    return true;
  }

  @SuppressWarnings("deprecation")
  public boolean validConstructor(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    if (PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, AllArgsConstructor.class, Value.class, lombok.experimental.Value.class)) {
      return true;
    }

    final Collection<PsiField> constructorParameters = filterFields(psiClass);

    if (PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, RequiredArgsConstructor.class, Data.class)) {
      final Collection<PsiField> requiredConstructorParameters = requiredArgsConstructorProcessor.getRequiredFields(psiClass);
      if (constructorParameters.size() == requiredConstructorParameters.size()) {
        return true;
      }
    }

    final Collection<PsiMethod> classConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);

    boolean constructorExists = false;
    for (PsiMethod classConstructor : classConstructors) {
      if (classConstructor.getParameterList().getParametersCount() == constructorParameters.size()) {
        constructorExists = true;
        break;
      }
    }

    if (!constructorExists) {
      builder.addWarning("@Wither needs constructor for all fields (%d parameters)", constructorParameters.size());
    }
    return constructorExists;
  }

  private Collection<PsiField> filterFields(@NotNull PsiClass psiClass) {
    final Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);

    Collection<PsiField> result = new ArrayList<PsiField>(psiFields.size());
    for (PsiField classField : psiFields) {
      final String classFieldName = classField.getName();
      if (classFieldName.startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER)) {
        continue;
      }
      if (classField.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      if (classField.hasModifierProperty(PsiModifier.FINAL) && null != classField.getInitializer()) {
        continue;
      }

      result.add(classField);
    }
    return result;
  }

  @Nullable
  public PsiMethod createWitherMethod(@NotNull PsiField psiField, @NotNull String methodModifier, @NotNull AccessorsInfo accessorsInfo) {
    LombokLightMethodBuilder result = null;
    final PsiClass psiFieldContainingClass = psiField.getContainingClass();
    if (psiFieldContainingClass != null) {
      final PsiType returnType = PsiClassUtil.getTypeWithGenerics(psiFieldContainingClass);
      final String psiFieldName = psiField.getName();
      final PsiType psiFieldType = psiField.getType();

      result = new LombokLightMethodBuilder(psiField.getManager(), getWitherName(accessorsInfo, psiFieldName, psiFieldType))
        .withMethodReturnType(returnType)
        .withContainingClass(psiFieldContainingClass)
        .withNavigationElement(psiField)
        .withModifier(methodModifier);

      PsiAnnotation witherAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, Wither.class);
      addOnXAnnotations(witherAnnotation, result.getModifierList(), "onMethod");

      final LombokLightParameter methodParameter = new LombokLightParameter(psiFieldName, psiFieldType, result, JavaLanguage.INSTANCE);
      PsiModifierList methodParameterModifierList = methodParameter.getModifierList();
      copyAnnotations(psiField, methodParameterModifierList,
        LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN, LombokUtils.DEPRECATED_PATTERN);
      addOnXAnnotations(witherAnnotation, methodParameterModifierList, "onParam");
      result.withParameter(methodParameter);

      if (psiFieldContainingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        result.withModifier(PsiModifier.ABSTRACT);
      } else {
        result.withBody(createCodeBlock(psiField, psiFieldContainingClass, returnType, psiFieldName));
      }
    }
    return result;
  }

  @NotNull
  private PsiCodeBlock createCodeBlock(@NotNull PsiField psiField, PsiClass psiFieldContainingClass, PsiType returnType, String psiFieldName) {
    final String blockText;
    if (isShouldGenerateFullBodyBlock()) {
      final String paramString = getConstructorCall(psiField, psiFieldContainingClass);
      blockText = String.format("return this.%s == %s ? this : new %s(%s);", psiFieldName, psiFieldName, returnType.getCanonicalText(), paramString);
    } else {
      blockText = "return null;";
    }
    return PsiMethodUtil.createCodeBlockFromText(blockText, psiFieldContainingClass);
  }

  private AccessorsInfo buildAccessorsInfo(@NotNull PsiField psiField) {
    return AccessorsInfo.build(psiField).withFluent(false);
  }

  private String getWitherName(@NotNull AccessorsInfo accessorsInfo, String psiFieldName, PsiType psiFieldType) {
    return LombokUtils.toWitherName(accessorsInfo, psiFieldName, PsiType.BOOLEAN.equals(psiFieldType));
  }

  private String getConstructorCall(@NotNull PsiField psiField, @NotNull PsiClass psiClass) {
    final StringBuilder paramString = new StringBuilder();
    final Collection<PsiField> psiFields = filterFields(psiClass);
    for (PsiField classField : psiFields) {
      final String classFieldName = classField.getName();
      if (classField.equals(psiField)) {
        paramString.append(classFieldName);
      } else {
        paramString.append("this.").append(classFieldName);
      }
      paramString.append(',');
    }
    if (paramString.length() > 1) {
      paramString.deleteCharAt(paramString.length() - 1);
    }
    return paramString.toString();
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}
