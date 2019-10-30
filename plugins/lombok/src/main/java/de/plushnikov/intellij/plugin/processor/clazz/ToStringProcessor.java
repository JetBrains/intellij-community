package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.handler.EqualsAndHashCodeToStringHandler;
import de.plushnikov.intellij.plugin.processor.handler.EqualsAndHashCodeToStringHandler.MemberInfo;
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

  private static final String INCLUDE_ANNOTATION_METHOD = "name";
  private static final String INCLUDE_ANNOTATION_RANK = "rank";
  private static final String INCLUDE_ANNOTATION_SKIP_NULL = "skipNull";
  private static final String TOSTRING_INCLUDE = ToString.Include.class.getCanonicalName();
  private static final String TOSTRING_EXCLUDE = ToString.Exclude.class.getCanonicalName();

  private final EqualsAndHashCodeToStringHandler handler;

  public ToStringProcessor(@NotNull EqualsAndHashCodeToStringHandler equalsAndHashCodeToStringHandler) {
    super(PsiMethod.class, ToString.class);
    handler = equalsAndHashCodeToStringHandler;
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

    final Collection<MemberInfo> memberInfos = handler.filterFields(psiClass, psiAnnotation, false, INCLUDE_ANNOTATION_METHOD);
    final PsiMethod stringMethod = createToStringMethod(psiClass, memberInfos, psiAnnotation, false);
    return Collections.singletonList(stringMethod);
  }

  @NotNull
  public PsiMethod createToStringMethod(@NotNull PsiClass psiClass, @NotNull Collection<MemberInfo> memberInfos, @NotNull PsiAnnotation psiAnnotation, boolean forceCallSuper) {
    final PsiManager psiManager = psiClass.getManager();

    final String paramString = createParamString(psiClass, memberInfos, psiAnnotation, forceCallSuper);
    final String blockText = String.format("return \"%s(%s)\";", getSimpleClassName(psiClass), paramString);

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, METHOD_NAME)
      .withMethodReturnType(PsiType.getJavaLangString(psiManager, GlobalSearchScope.allScope(psiClass.getProject())))
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PUBLIC);
    methodBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(blockText, methodBuilder));
    return methodBuilder;
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

  private String createParamString(@NotNull PsiClass psiClass, @NotNull Collection<MemberInfo> memberInfos, @NotNull PsiAnnotation psiAnnotation, boolean forceCallSuper) {
    final boolean callSuper = forceCallSuper || readCallSuperAnnotationOrConfigProperty(psiAnnotation, psiClass, ConfigKey.TOSTRING_CALL_SUPER);
    final boolean doNotUseGetters = readAnnotationOrConfigProperty(psiAnnotation, psiClass, "doNotUseGetters", ConfigKey.TOSTRING_DO_NOT_USE_GETTERS);
    final boolean includeFieldNames = readAnnotationOrConfigProperty(psiAnnotation, psiClass, "includeFieldNames", ConfigKey.TOSTRING_INCLUDE_FIELD_NAMES);

    final StringBuilder paramString = new StringBuilder();
    if (callSuper) {
      paramString.append("super=\" + super.toString() + \", ");
    }

    for (MemberInfo memberInfo : memberInfos) {

      if (includeFieldNames) {
        paramString.append(memberInfo.getName()).append('=');
      }
      paramString.append("\"+");

      final PsiType classFieldType = memberInfo.getType();
      if (classFieldType instanceof PsiArrayType) {
        final PsiType componentType = ((PsiArrayType) classFieldType).getComponentType();
        if (componentType instanceof PsiPrimitiveType) {
          paramString.append("java.util.Arrays.toString(");
        } else {
          paramString.append("java.util.Arrays.deepToString(");
        }
      }

      final String memberAccessor = handler.getMemberAccessorName(memberInfo, doNotUseGetters, psiClass);
      paramString.append("this.").append(memberAccessor);

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

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addFieldsAnnotation(result, psiClass, TOSTRING_INCLUDE, TOSTRING_EXCLUDE);
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      final String psiFieldName = StringUtil.notNullize(psiField.getName());
      if (handler.filterFields(containingClass, psiAnnotation, false, INCLUDE_ANNOTATION_METHOD).stream()
        .map(MemberInfo::getName).anyMatch(psiFieldName::equals)) {
        return LombokPsiElementUsage.READ;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
