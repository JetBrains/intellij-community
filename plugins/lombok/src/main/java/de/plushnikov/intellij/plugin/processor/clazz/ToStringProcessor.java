package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Inspect and validate @ToString lombok annotation on a class
 * Creates toString() method for fields of this class
 *
 * @author Plushnikov Michail
 */
public class ToStringProcessor extends AbstractClassProcessor {

  public static final String METHOD_NAME = "toString";

  public ToStringProcessor() {
    super(PsiMethod.class, ToString.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final boolean result = validateAnnotationOnRigthType(psiClass, builder);
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

    return result;
  }

  private boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("@ToString is only supported on a class or enum type");
      result = false;
    }
    return result;
  }

  private boolean validateExistingMethods(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    if (PsiMethodUtil.hasMethodByName(classMethods, METHOD_NAME)) {
      builder.addWarning("Not generated '%s'(): A method with same name already exists", METHOD_NAME);
      result = false;
    }

    return result;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    target.addAll(createToStringMethod(psiClass, psiAnnotation));
  }

  @NotNull
  Collection<PsiMethod> createToStringMethod(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    if (PsiMethodUtil.hasMethodByName(classMethods, METHOD_NAME)) {
      return Collections.emptyList();
    }

    final Collection<PsiField> psiFields = filterFields(psiClass, psiAnnotation, false);
    final PsiMethod stringMethod = createToStringMethod(psiClass, psiFields, psiAnnotation);
    return Collections.singletonList(stringMethod);
  }

  @NotNull
  public PsiMethod createToStringMethod(@NotNull PsiClass psiClass, @NotNull Collection<PsiField> psiFields, @NotNull PsiAnnotation psiAnnotation) {
    final PsiManager psiManager = psiClass.getManager();

    return new LombokLightMethodBuilder(psiManager, METHOD_NAME)
      .withMethodReturnType(PsiType.getJavaLangString(psiManager, GlobalSearchScope.allScope(psiClass.getProject())))
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PUBLIC)
      .withBody(createCodeBlock(psiClass, psiFields, psiAnnotation));
  }

  @NotNull
  private PsiCodeBlock createCodeBlock(@NotNull PsiClass psiClass, @NotNull Collection<PsiField> psiFields, @NotNull PsiAnnotation psiAnnotation) {
    final String blockText;
    if (isShouldGenerateFullBodyBlock()) {
      final String paramString = createParamString(psiClass, psiFields, psiAnnotation);
      blockText = String.format("return \"%s(%s)\";", getSimpleClassName(psiClass), paramString);
    } else {
      blockText = "return \"\";";
    }
    return PsiMethodUtil.createCodeBlockFromText(blockText, psiClass);
  }

  private String getSimpleClassName(@NotNull PsiClass psiClass) {
    final StringBuilder psiClassName = new StringBuilder();

    PsiClass containingClass = psiClass;
    do {
      if (psiClassName.length() > 0) {
        psiClassName.insert(0, '.');
      }
      psiClassName.insert(0, containingClass.getName());
      containingClass = containingClass.getContainingClass();
    } while (null != containingClass);

    return psiClassName.toString();
  }

  private String createParamString(@NotNull PsiClass psiClass, @NotNull Collection<PsiField> psiFields, @NotNull PsiAnnotation psiAnnotation) {
    final boolean callSuper = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "callSuper", false);
    final boolean doNotUseGetters = readAnnotationOrConfigProperty(psiAnnotation, psiClass, "doNotUseGetters", ConfigKey.TOSTRING_DO_NOT_USE_GETTERS);
    final boolean includeFieldNames = readAnnotationOrConfigProperty(psiAnnotation, psiClass, "includeFieldNames", ConfigKey.TOSTRING_INCLUDE_FIELD_NAMES);

    final StringBuilder paramString = new StringBuilder();
    if (callSuper) {
      paramString.append("super=\" + super.toString() + \", ");
    }

    for (PsiField classField : psiFields) {
      final String fieldName = classField.getName();

      if (includeFieldNames) {
        paramString.append(fieldName).append('=');
      }
      paramString.append("\"+");

      final PsiType classFieldType = classField.getType();
      if (classFieldType instanceof PsiArrayType) {
        final PsiType componentType = ((PsiArrayType) classFieldType).getComponentType();
        if (componentType instanceof PsiPrimitiveType) {
          paramString.append("java.util.Arrays.toString(");
        } else {
          paramString.append("java.util.Arrays.deepToString(");
        }
      }

      final String fieldAccessor = buildAttributeNameString(doNotUseGetters, classField, psiClass);
      paramString.append("this.").append(fieldAccessor);

      if (classFieldType instanceof PsiArrayType) {
        paramString.append(")");
      }

      paramString.append("+\", ");
    }
    if (paramString.length() > 2) {
      paramString.delete(paramString.length() - 2, paramString.length());
    }
    return paramString.toString();
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      if (PsiClassUtil.getNames(filterFields(containingClass, psiAnnotation, false)).contains(psiField.getName())) {
        return LombokPsiElementUsage.READ;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
