package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKeys;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Inspect and validate @EqualsAndHashCode lombok annotation on a class
 * Creates equals/hashcode method for fields of this class
 *
 * @author Plushnikov Michail
 */
public class EqualsAndHashCodeProcessor extends AbstractClassProcessor {

  public static final String EQUALS_METHOD_NAME = "equals";
  public static final String HASH_CODE_METHOD_NAME = "hashCode";
  public static final String CAN_EQUAL_METHOD_NAME = "canEqual";

  public EqualsAndHashCodeProcessor() {
    super(EqualsAndHashCode.class, PsiMethod.class);
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
      builder.addWarning("exclude and of are mutually exclusive; the 'exclude' parameter will be ignored",
          PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "exclude", null));
    } else {
      validateExcludeParam(psiClass, builder, psiAnnotation, excludeProperty);
    }
    validateOfParam(psiClass, builder, psiAnnotation, ofProperty);

    validateCallSuperParam(psiAnnotation, psiClass, builder, "equals/hashCode");
    validateCallSuperParamForObject(psiAnnotation, psiClass, builder);

    return result;
  }

  protected void validateCallSuperParamForObject(PsiAnnotation psiAnnotation, PsiClass psiClass, ProblemBuilder builder) {
    boolean callSuperProperty = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "callSuper", false);
    if (callSuperProperty && !PsiClassUtil.hasSuperClass(psiClass)) {
      builder.addError("Generating equals/hashCode with a supercall to java.lang.Object is pointless.",
          PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", "false"),
          PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", null));
    }
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("@EqualsAndHashCode is only supported on a class type");
      result = false;
    }
    return result;
  }

  protected boolean validateExistingMethods(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    if (PsiMethodUtil.hasMethodByName(classMethods, EQUALS_METHOD_NAME, HASH_CODE_METHOD_NAME)) {
      builder.addWarning("Not generating equals and hashCode: A method with one of those names already exists. (Either both or none of these methods will be generated).");
      return false;
    }
    return true;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    target.addAll(createEqualAndHashCode(psiClass, psiAnnotation));
  }

  protected Collection<PsiMethod> createEqualAndHashCode(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    if (PsiMethodUtil.hasMethodByName(classMethods, EQUALS_METHOD_NAME, HASH_CODE_METHOD_NAME)) {
      return Collections.emptyList();
    }

    final boolean shouldGenerateCanEqual = shouldGenerateCanEqual(psiClass);

    Collection<PsiMethod> result = new ArrayList<PsiMethod>(3);
    result.add(createEqualsMethod(psiClass, psiAnnotation, shouldGenerateCanEqual));
    result.add(createHashCodeMethod(psiClass, psiAnnotation, shouldGenerateCanEqual));

    if (shouldGenerateCanEqual && !PsiMethodUtil.hasMethodByName(classMethods, CAN_EQUAL_METHOD_NAME)) {
      result.add(createCanEqualMethod(psiClass, psiAnnotation));
    }
    return result;
  }

  @SuppressWarnings("deprecation")
  private boolean shouldGenerateCanEqual(@NotNull PsiClass psiClass) {
    final boolean isNotDirectDescendantOfObject = PsiClassUtil.hasSuperClass(psiClass);
    if (isNotDirectDescendantOfObject) {
      return true;
    }

    final boolean isFinal = psiClass.hasModifierProperty(PsiModifier.FINAL) ||
        (PsiAnnotationUtil.isAnnotatedWith(psiClass, Value.class, lombok.experimental.Value.class) && PsiAnnotationUtil.isNotAnnotatedWith(psiClass, NonFinal.class));
    return !isFinal;
  }

  @NotNull
  private PsiMethod createEqualsMethod(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, boolean hasCanEqualMethod) {
    final PsiManager psiManager = psiClass.getManager();

    final String blockText = createEqualsBlockString(psiClass, psiAnnotation, hasCanEqualMethod);

    LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, EQUALS_METHOD_NAME)
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(PsiType.BOOLEAN)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withBody(PsiMethodUtil.createCodeBlockFromText(blockText, psiClass));

    final LombokLightParameter methodParameter = new LombokLightParameter("o", PsiType.getJavaLangObject(
        psiManager, GlobalSearchScope.allScope(psiClass.getProject())), methodBuilder, JavaLanguage.INSTANCE);
    addOnXAnnotations(psiAnnotation, methodParameter.getModifierList(), "onParam");

    return methodBuilder.withParameter(methodParameter);
  }

  @NotNull
  private PsiMethod createHashCodeMethod(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, boolean hasCanEqualMethod) {
    final PsiManager psiManager = psiClass.getManager();

    final String blockText = createHashcodeBlockString(psiClass, psiAnnotation);

    return new LombokLightMethodBuilder(psiManager, HASH_CODE_METHOD_NAME)
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(PsiType.INT)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withBody(PsiMethodUtil.createCodeBlockFromText(blockText, psiClass));
  }

  @NotNull
  private PsiMethod createCanEqualMethod(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final PsiManager psiManager = psiClass.getManager();

    final String blockText = String.format("return other instanceof %s;", psiClass.getName());

    LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, CAN_EQUAL_METHOD_NAME)
        .withModifier(PsiModifier.PROTECTED)
        .withMethodReturnType(PsiType.BOOLEAN)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withBody(PsiMethodUtil.createCodeBlockFromText(blockText, psiClass));

    final LombokLightParameter methodParameter = new LombokLightParameter("other", PsiType.getJavaLangObject(
        psiManager, GlobalSearchScope.allScope(psiClass.getProject())), methodBuilder, JavaLanguage.INSTANCE);
    addOnXAnnotations(psiAnnotation, methodParameter.getModifierList(), "onParam");

    return methodBuilder.withParameter(methodParameter);
  }

  private String createEqualsBlockString(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, boolean hasCanEqualMethod) {
    final boolean callSuper = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "callSuper", false);
    final boolean doNotUseGetters = readAnnotationOrConfigProperty(psiAnnotation, psiClass, "doNotUseGetters", ConfigKeys.EQUALSANDHASHCODE_DO_NOT_USE_GETTERS);

    final String psiClassName = psiClass.getName();

    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("if (o == this) return true;\n");
      builder.append("if (!(o instanceof ").append(psiClassName).append(")) return false;\n");
      builder.append("final ").append(psiClassName).append(" other = (").append(psiClassName).append(")o;\n");

      if (hasCanEqualMethod) {
        builder.append("if (!other.canEqual((java.lang.Object)this)) return false;\n");
      }
      if (callSuper) {
        builder.append("if (!super.equals(o)) return false;\n");
      }

      final Collection<PsiField> psiFields = filterFields(psiClass, psiAnnotation, true);
      for (PsiField classField : psiFields) {
        final String fieldName = classField.getName();

        final String fieldAccessor = buildAttributeNameString(doNotUseGetters, classField, psiClass);

        final PsiType classFieldType = classField.getType();
        if (classFieldType instanceof PsiPrimitiveType) {
          if (PsiType.FLOAT.equals(classFieldType)) {
            builder.append("if (java.lang.Float.compare(this.").append(fieldAccessor).append(", other.").append(fieldAccessor).append(") != 0) return false;\n");
          } else if (PsiType.DOUBLE.equals(classFieldType)) {
            builder.append("if (java.lang.Double.compare(this.").append(fieldAccessor).append(", other.").append(fieldAccessor).append(") != 0) return false;\n");
          } else {
            builder.append("if (this.").append(fieldAccessor).append(" != other.").append(fieldAccessor).append(") return false;\n");
          }
        } else if (classFieldType instanceof PsiArrayType) {
          final PsiType componentType = ((PsiArrayType) classFieldType).getComponentType();
          if (componentType instanceof PsiPrimitiveType) {
            builder.append("if (!java.util.Arrays.equals(this.").append(fieldAccessor).append(", other.").append(fieldAccessor).append(")) return false;\n");
          } else {
            builder.append("if (!java.util.Arrays.deepEquals(this.").append(fieldAccessor).append(", other.").append(fieldAccessor).append(")) return false;\n");
          }
        } else {
          builder.append("final java.lang.Object this$").append(fieldName).append(" = this.").append(fieldAccessor).append(";\n");
          builder.append("final java.lang.Object other$").append(fieldName).append(" = other.").append(fieldAccessor).append(";\n");
          builder.append("if (this$").append(fieldName).append(" == null ? other$").append(fieldName).append(" != null : !this$")
              .append(fieldName).append(".equals(other$").append(fieldName).append(")) return false;\n");
        }
      }
      builder.append("return true;\n");
      return builder.toString();
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private static final int PRIME_FOR_HASHCODE = 59;
  private static final int PRIME_FOR_TRUE = 79;
  private static final int PRIME_FOR_FALSE = 97;

  private String createHashcodeBlockString(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final boolean callSuper = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "callSuper", false);
    final boolean doNotUseGetters = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "doNotUseGetters", false);

    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {

      final Collection<PsiField> psiFields = filterFields(psiClass, psiAnnotation, true);

      if (!psiFields.isEmpty() || callSuper) {
        builder.append("final int PRIME = ").append(PRIME_FOR_HASHCODE).append(";\n");
      }
      builder.append("int result = 1;\n");

      if (callSuper) {
        builder.append("result = result * PRIME + super.hashCode();\n");
      }

      for (PsiField classField : psiFields) {
        final String fieldName = classField.getName();

        final String fieldAccessor = buildAttributeNameString(doNotUseGetters, classField, psiClass);

        final PsiType classFieldType = classField.getType();
        if (classFieldType instanceof PsiPrimitiveType) {
          if (PsiType.BOOLEAN.equals(classFieldType)) {
            builder.append("result = result * PRIME + (this.").append(fieldAccessor).append(" ? ").append(PRIME_FOR_TRUE).append(" : ").append(PRIME_FOR_FALSE).append(");\n");
          } else if (PsiType.LONG.equals(classFieldType)) {
            builder.append("final long $").append(fieldName).append(" = this.").append(fieldAccessor).append(";\n");
            builder.append("result = result * PRIME + (int)($").append(fieldName).append(" >>> 32 ^ $").append(fieldName).append(");\n");
          } else if (PsiType.FLOAT.equals(classFieldType)) {
            builder.append("result = result * PRIME + java.lang.Float.floatToIntBits(this.").append(fieldAccessor).append(");\n");
          } else if (PsiType.DOUBLE.equals(classFieldType)) {
            builder.append("final long $").append(fieldName).append(" = java.lang.Double.doubleToLongBits(this.").append(fieldAccessor).append(");\n");
            builder.append("result = result * PRIME + (int)($").append(fieldName).append(" >>> 32 ^ $").append(fieldName).append(");\n");
          } else {
            builder.append("result = result * PRIME + this.").append(fieldAccessor).append(";\n");
          }
        } else if (classFieldType instanceof PsiArrayType) {
          final PsiType componentType = ((PsiArrayType) classFieldType).getComponentType();
          if (componentType instanceof PsiPrimitiveType) {
            builder.append("result = result * PRIME + java.util.Arrays.hashCode(this.").append(fieldName).append(");\n");
          } else {
            builder.append("result = result * PRIME + java.util.Arrays.deepHashCode(this.").append(fieldName).append(");\n");
          }
        } else {
          builder.append("final java.lang.Object $").append(fieldName).append(" = this.").append(fieldAccessor).append(";\n");
          builder.append("result = result * PRIME + ($").append(fieldName).append(" == null ? 0 : $").append(fieldName).append(".hashCode());\n");
        }
      }
      builder.append("return result;\n");
      return builder.toString();
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


}
