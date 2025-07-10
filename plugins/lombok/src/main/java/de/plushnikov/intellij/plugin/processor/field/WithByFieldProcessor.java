package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AbstractConstructorClassProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightModifierList;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokAddNullAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class WithByFieldProcessor extends AbstractFieldProcessor {
  @NonNls public static final String WITH_BY_METHOD_PARAMETER_NAME = "transformer";

  public WithByFieldProcessor() {
    super(PsiMethod.class, LombokClassNames.WITH_BY);
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass,
                                                                   @NotNull PsiAnnotation psiAnnotation,
                                                                   @NotNull PsiField psiField) {
    final AccessorsInfo accessorsInfo = buildAccessorsInfo(psiField);
    return Collections.singletonList(LombokUtils.getWithByName(psiField, accessorsInfo));
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemSink builder) {
    validateOnXAnnotations(psiAnnotation, psiField, builder, "onParam");

    final PsiClass containingClass = psiField.getContainingClass();

    boolean valid = null != containingClass;

    valid &= validateVisibility(psiAnnotation);

    final AccessorsInfo accessorsInfo = buildAccessorsInfo(psiField);
    final String withByMethodName = LombokUtils.getWithByName(psiField, accessorsInfo);

    valid &= validMethodName(withByMethodName, builder);
    valid &= validName(psiField, withByMethodName, builder);
    valid &= validNonStatic(psiField, withByMethodName, builder);
    valid &= validNonFinalInitialized(psiField, withByMethodName, builder);

    if (valid) {
      final Collection<PsiMethod> existingMethods = filterToleratedElements(PsiClassUtil.collectClassMethodsIntern(containingClass));
      valid = validIsWithByUnique(psiField, accessorsInfo, existingMethods, builder);
    }

    return valid;
  }

  private static boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  private static boolean validMethodName(@Nullable String withByMethodName, @NotNull ProblemSink builder) {
    if (Strings.isEmpty(withByMethodName)) {
      builder.addWarningMessage("inspection.message.withby.not.generating.filed.name.not.fit", withByMethodName);
      return false;
    }
    return true;
  }

  private static boolean validName(@NotNull PsiField psiField, @NotNull String withByMethodName, @NotNull ProblemSink builder) {
    if (psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER)) {
      builder.addWarningMessage("inspection.message.withby.not.generating.field.name.reserved", withByMethodName);
      return false;
    }
    return true;
  }

  private static boolean validNonStatic(@NotNull PsiField psiField, @NotNull String withByMethodName, final @NotNull ProblemSink builder) {
    if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
      builder.addWarningMessage("inspection.message.withby.not.generating.field.static", withByMethodName)
        .withLocalQuickFixes(() -> PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.STATIC, false, false));
      return false;
    }
    return true;
  }

  private static boolean validNonFinalInitialized(@NotNull PsiField psiField,
                                                  @NotNull String withByMethodName,
                                                  @NotNull ProblemSink builder) {
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass &&
        psiField.hasModifierProperty(PsiModifier.FINAL) && !PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.VALUE) &&
        psiField.hasInitializer() && !PsiAnnotationSearchUtil.isAnnotatedWith(psiField, LombokClassNames.BUILDER_DEFAULT)) {
      builder.addWarningMessage("inspection.message.withby.not.generating.field.final.initialized", withByMethodName)
        .withLocalQuickFixes(() -> PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, false, false));
      return false;
    }
    return true;
  }

  private static boolean validIsWithByUnique(@NotNull PsiField psiField,
                                             @NotNull AccessorsInfo accessorsInfo,
                                             @NotNull Collection<PsiMethod> existingMethods,
                                             @NotNull ProblemSink builder) {
    final Collection<String> possibleMethodNames =
      LombokUtils.toAllWithByNames(accessorsInfo, psiField.getName(), PsiTypes.booleanType().equals(psiField.getType()));
    for (String withByName : possibleMethodNames) {
      if (PsiMethodUtil.hasSimilarMethod(existingMethods, withByName, 1)) {
        builder.addWarningMessage("inspection.message.withby.not.generating.method.already.exists", withByName);
        return false;
      }
    }
    return true;
  }

  private static AccessorsInfo buildAccessorsInfo(@NotNull PsiField psiField) {
    return AccessorsInfo.buildFor(psiField).withFluent(false);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiField psiField,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target, @Nullable String nameHint) {
    String methodModifier = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodModifier != null) {
      AccessorsInfo accessorsInfo = buildAccessorsInfo(psiField);
      PsiMethod method = createWithByMethod(psiField, methodModifier, accessorsInfo, nameHint);
      if (method != null) {
        target.add(method);
      }
    }
  }

  public @Nullable PsiMethod createWithByMethod(@NotNull PsiField psiField,
                                                @NotNull String methodModifier,
                                                @NotNull AccessorsInfo accessorsInfo,
                                                @Nullable String nameHint) {

    LombokLightMethodBuilder methodBuilder = null;
    final PsiClass psiFieldContainingClass = psiField.getContainingClass();
    if (psiFieldContainingClass != null) {
      final String withByName = LombokUtils.getWithByName(psiField, accessorsInfo);
      if (nameHint != null && !nameHint.equals(withByName)) return null;

      final PsiType returnType = PsiClassUtil.getTypeWithGenerics(psiFieldContainingClass);

      methodBuilder = new LombokLightMethodBuilder(psiField.getManager(), withByName)
        .withMethodReturnType(returnType)
        .withContainingClass(psiFieldContainingClass)
        .withNavigationElement(psiField)
        .withModifier(methodModifier)
        .withPureContract()
        .withWriteAccess();

      if (accessorsInfo.isMakeFinal()) {
        methodBuilder.withModifier(PsiModifier.FINAL);
      }

      PsiAnnotation withByAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, LombokClassNames.WITH_BY);
      LombokCopyableAnnotations.copyOnXAnnotations(withByAnnotation, methodBuilder.getModifierList(), "onMethod");

      final WithByParts withByParts = WithByParts.create(psiField);

      final PsiType withByMethodParameterType;
      if (null != withByParts.parameterizer) {
        withByMethodParameterType =
          PsiTypeUtil.createCollectionType(psiField.getManager(), withByParts.functionalInterfaceName,
                                           withByParts.parameterizer.toArray(PsiType.EMPTY_ARRAY));
      }
      else {
        withByMethodParameterType = PsiTypeUtil.createCollectionType(psiField.getManager(), withByParts.functionalInterfaceName);
      }

      final LombokLightParameter methodParameter =
        new LombokLightParameter(WITH_BY_METHOD_PARAMETER_NAME, withByMethodParameterType, methodBuilder);
      methodParameter.setModifiers(PsiModifier.FINAL);
      methodBuilder.withParameter(methodParameter);
      LombokLightModifierList methodParameterModifierList = methodParameter.getModifierList();
      LombokCopyableAnnotations.copyCopyableAnnotations(psiField, methodParameterModifierList, LombokCopyableAnnotations.BASE_COPYABLE);

      final String paramString = getConstructorCall(psiField, psiFieldContainingClass, withByParts);
      final String blockText = String.format("return new %s(%s);", returnType.getCanonicalText(), paramString);
      methodBuilder.withBodyText(blockText);

      LombokAddNullAnnotations.createRelevantNonNullAnnotation(psiFieldContainingClass, methodBuilder);
    }
    return methodBuilder;
  }

  private static String getConstructorCall(@NotNull PsiField psiElement, @NotNull PsiClass psiClass, @NotNull WithByParts withByParts) {
    final StringBuilder paramString = new StringBuilder();

    final Collection<PsiField> requiredFields = AbstractConstructorClassProcessor.getRequiredFields(psiClass);
    for (PsiField psiField : requiredFields) {
      final String classFieldName = psiField.getName();
      if (psiField.equals(psiElement)) {
        if (Strings.isNotEmpty(withByParts.requiredCast())) {
          paramString.append('(').append(withByParts.requiredCast()).append(')');
        }
        paramString.append(WITH_BY_METHOD_PARAMETER_NAME).append('.').append(withByParts.applyMethodName());
        paramString.append('(').append("this.").append(classFieldName).append(')');
      }
      else {
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

  private static final String NAME_JUF_FUNCTION = "java.util.function.Function";
  private static final String NAME_JUF_OP = "java.util.function.UnaryOperator";
  private static final String NAME_JUF_DOUBLEOP = "java.util.function.DoubleUnaryOperator";
  private static final String NAME_JUF_INTOP = "java.util.function.IntUnaryOperator";
  private static final String NAME_JUF_LONGOP = "java.util.function.LongUnaryOperator";

  private record WithByParts(String applyMethodName, Collection<PsiType> parameterizer,
                             String functionalInterfaceName, String requiredCast) {
    public static WithByParts create(@NotNull PsiVariable psiVariable) {
      final PsiType psiFieldType = psiVariable.getType();

      String functionalInterfaceName = null;

      String requiredCast = null;
      List<PsiType> parameterizer = Collections.emptyList();
      boolean superExtendsStyle = true;
      String applyMethodName = "apply";

      if (psiFieldType instanceof PsiPrimitiveType) {
        if (PsiTypes.charType().equals(psiFieldType)) {
          requiredCast = JavaKeywords.CHAR;
          functionalInterfaceName = NAME_JUF_INTOP;
        }
        else if (PsiTypes.shortType().equals(psiFieldType)) {
          requiredCast = JavaKeywords.SHORT;
          functionalInterfaceName = NAME_JUF_INTOP;
        }
        else if (PsiTypes.byteType().equals(psiFieldType)) {
          requiredCast = JavaKeywords.BYTE;
          functionalInterfaceName = NAME_JUF_INTOP;
        }
        else if (PsiTypes.intType().equals(psiFieldType)) {
          functionalInterfaceName = NAME_JUF_INTOP;
        }
        else if (PsiTypes.longType().equals(psiFieldType)) {
          functionalInterfaceName = NAME_JUF_LONGOP;
        }
        else if (PsiTypes.floatType().equals(psiFieldType)) {
          functionalInterfaceName = NAME_JUF_DOUBLEOP;
          requiredCast = JavaKeywords.FLOAT;
        }
        else if (PsiTypes.doubleType().equals(psiFieldType)) {
          functionalInterfaceName = NAME_JUF_DOUBLEOP;
        }
        else if (PsiTypes.booleanType().equals(psiFieldType)) {
          functionalInterfaceName = NAME_JUF_OP;
          final Project project = psiVariable.getProject();
          parameterizer = Collections.singletonList(
            PsiType.getTypeByName(CommonClassNames.JAVA_LANG_BOOLEAN, project, GlobalSearchScope.allScope(project)));
          superExtendsStyle = false;
        }
      }
      if (functionalInterfaceName == null) {
        functionalInterfaceName = NAME_JUF_FUNCTION;
        parameterizer = Collections.singletonList(psiFieldType);
      }

      if (NAME_JUF_INTOP.equals(functionalInterfaceName)) applyMethodName = "applyAsInt";
      if (NAME_JUF_LONGOP.equals(functionalInterfaceName)) applyMethodName = "applyAsLong";
      if (NAME_JUF_DOUBLEOP.equals(functionalInterfaceName)) applyMethodName = "applyAsDouble";

      if (!parameterizer.isEmpty() && superExtendsStyle) {
        final PsiType psiType = parameterizer.get(0);
        PsiType parameterizer1 = PsiWildcardType.createSuper(psiVariable.getManager(), psiType);
        PsiType parameterizer2 = PsiWildcardType.createExtends(psiVariable.getManager(), psiType);
        parameterizer = Arrays.asList(parameterizer1, parameterizer2);
      }

      return new WithByParts(applyMethodName, parameterizer, functionalInterfaceName, requiredCast);
    }
  }
}
