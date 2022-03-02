package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.handler.EqualsAndHashCodeToStringHandler;
import de.plushnikov.intellij.plugin.processor.handler.EqualsAndHashCodeToStringHandler.MemberInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Inspect and validate @EqualsAndHashCode lombok annotation on a class
 * Creates equals/hashcode method for fields of this class
 *
 * @author Plushnikov Michail
 */
public final class EqualsAndHashCodeProcessor extends AbstractClassProcessor {
  private static final String EQUALS_METHOD_NAME = "equals";
  private static final String HASH_CODE_METHOD_NAME = "hashCode";
  private static final String CAN_EQUAL_METHOD_NAME = "canEqual";

  private static final String INCLUDE_ANNOTATION_METHOD = "replaces";
  private static final String EQUALSANDHASHCODE_INCLUDE = LombokClassNames.EQUALS_AND_HASHCODE_INCLUDE;
  private static final String EQUALSANDHASHCODE_EXCLUDE = LombokClassNames.EQUALS_AND_HASHCODE_EXCLUDE;

  EqualsAndHashCodeProcessor() {
    super(PsiMethod.class, LombokClassNames.EQUALS_AND_HASHCODE);
  }

  private EqualsAndHashCodeToStringHandler getEqualsAndHashCodeToStringHandler() {
    return new EqualsAndHashCodeToStringHandler();
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@Nullable String nameHint, @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation) {
    return nameHint == null ||
           nameHint.equals(EQUALS_METHOD_NAME) ||
           nameHint.equals(HASH_CODE_METHOD_NAME) ||
           nameHint.equals(CAN_EQUAL_METHOD_NAME);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final boolean result = validateAnnotationOnRightType(psiClass, builder);
    if (result) {
      validateExistingMethods(psiClass, builder);
    }
    final Collection<String> excludeProperty = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "exclude", String.class);
    final Collection<String> ofProperty = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "of", String.class);

    if (!excludeProperty.isEmpty() && !ofProperty.isEmpty()) {
      builder.addWarning(LombokBundle.message("inspection.message.exclude.are.mutually.exclusive.exclude.parameter.will.be.ignored"),
                         PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "exclude", null));
    }
    else {
      validateExcludeParam(psiClass, builder, psiAnnotation, excludeProperty);
    }
    validateOfParam(psiClass, builder, psiAnnotation, ofProperty);

    validateCallSuperParamIntern(psiAnnotation, psiClass, builder);
    validateCallSuperParamForObject(psiAnnotation, psiClass, builder);

    return result;
  }

  private void validateCallSuperParamIntern(@NotNull PsiAnnotation psiAnnotation,
                                            @NotNull PsiClass psiClass,
                                            @NotNull ProblemBuilder builder) {
    validateCallSuperParam(psiAnnotation, psiClass, builder,
                           () -> PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", "false"),
                           () -> PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", "true"));
  }

  void validateCallSuperParamExtern(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    validateCallSuperParam(psiAnnotation, psiClass, builder,
                           () -> PsiQuickFixFactory.createAddAnnotationQuickFix(psiClass, "lombok.EqualsAndHashCode", "callSuper = true"));
  }

  private void validateCallSuperParam(@NotNull PsiAnnotation psiAnnotation,
                                      @NotNull PsiClass psiClass,
                                      @NotNull ProblemBuilder builder,
                                      Supplier<LocalQuickFix>... quickFixes) {
    final Boolean declaredBooleanAnnotationValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(psiAnnotation, "callSuper");
    if (null == declaredBooleanAnnotationValue) {
      final String configProperty = configDiscovery.getStringLombokConfigProperty(ConfigKey.EQUALSANDHASHCODE_CALL_SUPER, psiClass);
      if (!"CALL".equalsIgnoreCase(configProperty) &&
          !"SKIP".equalsIgnoreCase(configProperty) &&
          PsiClassUtil.hasSuperClass(psiClass) &&
          !hasOneOfMethodsDefined(psiClass)) {
        builder.addWarning(LombokBundle.message("inspection.message.generating.equals.hashcode.implementation"),
                           ContainerUtil.map2Array(quickFixes, Supplier::get));
      }
    }
  }

  private void validateCallSuperParamForObject(PsiAnnotation psiAnnotation, PsiClass psiClass, ProblemBuilder builder) {
    boolean callSuperProperty = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "callSuper", false);
    if (callSuperProperty && !PsiClassUtil.hasSuperClass(psiClass)) {
      builder.addError(LombokBundle.message("inspection.message.generating.equals.hashcode.with.super.call"),
                       PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", "false"),
                       PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", null));
    }
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(LombokBundle.message("inspection.message.equals.and.hashcode.only.supported.on.class.type"));
      result = false;
    }
    return result;
  }

  private void validateExistingMethods(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    if (hasOneOfMethodsDefined(psiClass)) {
      builder.addWarning(LombokBundle.message("inspection.message.not.generating.equals.hashcode"));
    }
  }

  private boolean hasOneOfMethodsDefined(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> classMethodsIntern = PsiClassUtil.collectClassMethodsIntern(psiClass);
    return PsiMethodUtil.hasMethodByName(classMethodsIntern, EQUALS_METHOD_NAME, 1) ||
           PsiMethodUtil.hasMethodByName(classMethodsIntern, HASH_CODE_METHOD_NAME, 0);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target) {
    target.addAll(createEqualAndHashCode(psiClass, psiAnnotation));
  }

  Collection<PsiMethod> createEqualAndHashCode(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    if (hasOneOfMethodsDefined(psiClass)) {
      return Collections.emptyList();
    }

    final Collection<MemberInfo> memberInfos = getEqualsAndHashCodeToStringHandler()
      .filterFields(psiClass, psiAnnotation, true, INCLUDE_ANNOTATION_METHOD);

    final boolean shouldGenerateCanEqual = shouldGenerateCanEqual(psiClass);

    Collection<PsiMethod> result = new ArrayList<>(3);
    result.add(createEqualsMethod(psiClass, psiAnnotation, shouldGenerateCanEqual, memberInfos));

    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    if (shouldGenerateCanEqual && !PsiMethodUtil.hasMethodByName(classMethods, CAN_EQUAL_METHOD_NAME, 1)) {
      result.add(createCanEqualMethod(psiClass, psiAnnotation));
    }

    result.add(createHashCodeMethod(psiClass, psiAnnotation, memberInfos));
    return result;
  }

  private boolean shouldGenerateCanEqual(@NotNull PsiClass psiClass) {
    final boolean isNotDirectDescendantOfObject = PsiClassUtil.hasSuperClass(psiClass);
    if (isNotDirectDescendantOfObject) {
      return true;
    }

    final boolean isFinal = psiClass.hasModifierProperty(PsiModifier.FINAL) ||
                            (PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.VALUE) &&
                             PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.NON_FINAL));
    return !isFinal;
  }

  @NotNull
  private PsiMethod createEqualsMethod(@NotNull PsiClass psiClass,
                                       @NotNull PsiAnnotation psiAnnotation,
                                       boolean hasCanEqualMethod,
                                       Collection<MemberInfo> memberInfos) {
    final PsiManager psiManager = psiClass.getManager();

    final String blockText = createEqualsBlockString(psiClass, psiAnnotation, hasCanEqualMethod, memberInfos);
    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, EQUALS_METHOD_NAME)
      .withModifier(PsiModifier.PUBLIC)
      .withMethodReturnType(PsiType.BOOLEAN)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withFinalParameter("o", PsiType.getJavaLangObject(psiManager, psiClass.getResolveScope()));

    copyOnXAnnotationsForFirstParam(psiAnnotation, methodBuilder);

    methodBuilder.withBodyText(blockText);
    return methodBuilder;
  }

  @NotNull
  private PsiMethod createHashCodeMethod(@NotNull PsiClass psiClass,
                                         @NotNull PsiAnnotation psiAnnotation,
                                         Collection<MemberInfo> memberInfos) {
    final PsiManager psiManager = psiClass.getManager();

    final String blockText = createHashcodeBlockString(psiClass, psiAnnotation, memberInfos);
    return new LombokLightMethodBuilder(psiManager, HASH_CODE_METHOD_NAME)
      .withModifier(PsiModifier.PUBLIC)
      .withMethodReturnType(PsiType.INT)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withBodyText(blockText);
  }

  @NotNull
  private PsiMethod createCanEqualMethod(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final PsiManager psiManager = psiClass.getManager();

    final String blockText = String.format("return other instanceof %s;", PsiTypesUtil.getClassType(psiClass).getCanonicalText());
    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, CAN_EQUAL_METHOD_NAME)
      .withModifier(PsiModifier.PROTECTED)
      .withMethodReturnType(PsiType.BOOLEAN)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withFinalParameter("other", PsiType.getJavaLangObject(psiManager, psiClass.getResolveScope()));

    copyOnXAnnotationsForFirstParam(psiAnnotation, methodBuilder);

    methodBuilder.withBodyText(blockText);
    return methodBuilder;
  }

  private void copyOnXAnnotationsForFirstParam(@NotNull PsiAnnotation psiAnnotation, LombokLightMethodBuilder methodBuilder) {
    LombokLightParameter parameter = methodBuilder.getParameterList().getParameter(0);
    if (null != parameter) {
      PsiModifierList methodParameterModifierList = parameter.getModifierList();
      copyOnXAnnotations(psiAnnotation, methodParameterModifierList, "onParam");
    }
  }

  private String createEqualsBlockString(@NotNull PsiClass psiClass,
                                         @NotNull PsiAnnotation psiAnnotation,
                                         boolean hasCanEqualMethod,
                                         Collection<MemberInfo> memberInfos) {
    final boolean callSuper = readCallSuperAnnotationOrConfigProperty(psiAnnotation, psiClass, ConfigKey.EQUALSANDHASHCODE_CALL_SUPER);
    final boolean doNotUseGetters =
      readAnnotationOrConfigProperty(psiAnnotation, psiClass, "doNotUseGetters", ConfigKey.EQUALSANDHASHCODE_DO_NOT_USE_GETTERS);

    final String canonicalClassName = PsiTypesUtil.getClassType(psiClass).getCanonicalText();
    final String canonicalWildcardClassName = PsiClassUtil.getWildcardClassType(psiClass).getCanonicalText();

    final StringBuilder builder = new StringBuilder();

    builder.append("if (o == this) return true;\n");
    builder.append("if (!(o instanceof ").append(canonicalClassName).append(")) return false;\n");
    builder.append("final ").append(canonicalWildcardClassName).append(" other = (").append(canonicalWildcardClassName).append(")o;\n");

    if (hasCanEqualMethod) {
      builder.append("if (!other.canEqual((java.lang.Object)this)) return false;\n");
    }
    if (callSuper) {
      builder.append("if (!super.equals(o)) return false;\n");
    }

    EqualsAndHashCodeToStringHandler handler = getEqualsAndHashCodeToStringHandler();
    for (MemberInfo memberInfo : memberInfos) {
      final String memberAccessor = handler.getMemberAccessorName(memberInfo, doNotUseGetters, psiClass);

      final PsiType memberType = memberInfo.getType();
      if (memberType instanceof PsiPrimitiveType) {
        if (PsiType.FLOAT.equals(memberType)) {
          builder.append("if (java.lang.Float.compare(this.").append(memberAccessor).append(", other.").append(memberAccessor)
            .append(") != 0) return false;\n");
        }
        else if (PsiType.DOUBLE.equals(memberType)) {
          builder.append("if (java.lang.Double.compare(this.").append(memberAccessor).append(", other.").append(memberAccessor)
            .append(") != 0) return false;\n");
        }
        else {
          builder.append("if (this.").append(memberAccessor).append(" != other.").append(memberAccessor).append(") return false;\n");
        }
      }
      else if (memberType instanceof PsiArrayType) {
        final PsiType componentType = ((PsiArrayType)memberType).getComponentType();
        if (componentType instanceof PsiPrimitiveType) {
          builder.append("if (!java.util.Arrays.equals(this.").append(memberAccessor).append(", other.").append(memberAccessor)
            .append(")) return false;\n");
        }
        else {
          builder.append("if (!java.util.Arrays.deepEquals(this.").append(memberAccessor).append(", other.").append(memberAccessor)
            .append(")) return false;\n");
        }
      }
      else {
        final String memberName = memberInfo.getName();
        builder.append("final java.lang.Object this$").append(memberName).append(" = this.").append(memberAccessor).append(";\n");
        builder.append("final java.lang.Object other$").append(memberName).append(" = other.").append(memberAccessor).append(";\n");
        builder.append("if (this$").append(memberName).append(" == null ? other$").append(memberName).append(" != null : !this$")
          .append(memberName).append(".equals(other$").append(memberName).append(")) return false;\n");
      }
    }
    builder.append("return true;\n");
    return builder.toString();
  }

  private static final int PRIME_FOR_HASHCODE = 59;
  private static final int PRIME_FOR_TRUE = 79;
  private static final int PRIME_FOR_FALSE = 97;
  private static final int PRIME_FOR_NULL = 43;

  private String createHashcodeBlockString(@NotNull PsiClass psiClass,
                                           @NotNull PsiAnnotation psiAnnotation,
                                           Collection<MemberInfo> memberInfos) {
    final boolean callSuper = readCallSuperAnnotationOrConfigProperty(psiAnnotation, psiClass, ConfigKey.EQUALSANDHASHCODE_CALL_SUPER);
    final boolean doNotUseGetters =
      readAnnotationOrConfigProperty(psiAnnotation, psiClass, "doNotUseGetters", ConfigKey.EQUALSANDHASHCODE_DO_NOT_USE_GETTERS);

    final StringBuilder builder = new StringBuilder();

    if (!memberInfos.isEmpty()) {
      builder.append("final int PRIME = ").append(PRIME_FOR_HASHCODE).append(";\n");
    }
    builder.append("int result = ");

    if (callSuper) {
      builder.append("super.hashCode();\n");
    }
    else {
      builder.append("1;\n");
    }

    EqualsAndHashCodeToStringHandler handler = getEqualsAndHashCodeToStringHandler();
    for (MemberInfo memberInfo : memberInfos) {
      final String memberAccessor = handler.getMemberAccessorName(memberInfo, doNotUseGetters, psiClass);
      final String memberName = memberInfo.getMethod() == null ? memberInfo.getName() : "$" + memberInfo.getName();

      final PsiType classFieldType = memberInfo.getType();
      if (classFieldType instanceof PsiPrimitiveType) {
        if (PsiType.BOOLEAN.equals(classFieldType)) {
          builder.append("result = result * PRIME + (this.").append(memberAccessor).append(" ? ").append(PRIME_FOR_TRUE).append(" : ")
            .append(PRIME_FOR_FALSE).append(");\n");
        }
        else if (PsiType.LONG.equals(classFieldType)) {
          builder.append("final long $").append(memberName).append(" = this.").append(memberAccessor).append(";\n");
          builder.append("result = result * PRIME + (int)($").append(memberName).append(" >>> 32 ^ $").append(memberName).append(");\n");
        }
        else if (PsiType.FLOAT.equals(classFieldType)) {
          builder.append("result = result * PRIME + java.lang.Float.floatToIntBits(this.").append(memberAccessor).append(");\n");
        }
        else if (PsiType.DOUBLE.equals(classFieldType)) {
          builder.append("final long $").append(memberName).append(" = java.lang.Double.doubleToLongBits(this.").append(memberAccessor)
            .append(");\n");
          builder.append("result = result * PRIME + (int)($").append(memberName).append(" >>> 32 ^ $").append(memberName).append(");\n");
        }
        else {
          builder.append("result = result * PRIME + this.").append(memberAccessor).append(";\n");
        }
      }
      else if (classFieldType instanceof PsiArrayType) {
        final PsiType componentType = ((PsiArrayType)classFieldType).getComponentType();
        if (componentType instanceof PsiPrimitiveType) {
          builder.append("result = result * PRIME + java.util.Arrays.hashCode(this.").append(memberAccessor).append(");\n");
        }
        else {
          builder.append("result = result * PRIME + java.util.Arrays.deepHashCode(this.").append(memberAccessor).append(");\n");
        }
      }
      else {
        builder.append("final java.lang.Object $").append(memberName).append(" = this.").append(memberAccessor).append(";\n");
        builder.append("result = result * PRIME + ($").append(memberName).append(" == null ? " + PRIME_FOR_NULL + " : $").append(memberName)
          .append(".hashCode());\n");
      }
    }
    builder.append("return result;\n");
    return builder.toString();
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addFieldsAnnotation(result, psiClass, EQUALSANDHASHCODE_INCLUDE, EQUALSANDHASHCODE_EXCLUDE);
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      final String psiFieldName = StringUtil.notNullize(psiField.getName());
      if (getEqualsAndHashCodeToStringHandler().filterFields(containingClass, psiAnnotation, true, INCLUDE_ANNOTATION_METHOD).stream()
        .map(MemberInfo::getName).anyMatch(psiFieldName::equals)) {
        return LombokPsiElementUsage.READ;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
