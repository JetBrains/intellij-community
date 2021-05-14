package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public static final String TO_STRING_METHOD_NAME = "toString";

  private static final String INCLUDE_ANNOTATION_METHOD = "name";
  private static final String INCLUDE_ANNOTATION_RANK = "rank";
  private static final String INCLUDE_ANNOTATION_SKIP_NULL = "skipNull";
  private static final String TOSTRING_INCLUDE = LombokClassNames.TO_STRING_INCLUDE;
  private static final String TOSTRING_EXCLUDE = LombokClassNames.TO_STRING_EXCLUDE;

  public ToStringProcessor() {
    super(PsiMethod.class, LombokClassNames.TO_STRING);
  }

  private EqualsAndHashCodeToStringHandler getEqualsAndHashCodeToStringHandler() {
    return ApplicationManager.getApplication().getService(EqualsAndHashCodeToStringHandler.class);
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@Nullable String nameHint, @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation) {
    return nameHint == null || nameHint.equals(TO_STRING_METHOD_NAME);
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
      builder.addWarning(LombokBundle.message("inspection.message.exclude.are.mutually.exclusive.exclude.parameter.will.be.ignored"),
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
      builder.addError(LombokBundle.message("inspection.message.to.string.only.supported.on.class.or.enum.type"));
      result = false;
    }
    return result;
  }

  private void validateExistingMethods(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    if (hasToStringMethodDefined(psiClass)) {
      builder.addWarning(LombokBundle.message("inspection.message.not.generated.s.method.with.same.name.already.exists"), TO_STRING_METHOD_NAME);
    }
  }

  private boolean hasToStringMethodDefined(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    return PsiMethodUtil.hasMethodByName(classMethods, TO_STRING_METHOD_NAME, 0);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    target.addAll(createToStringMethod(psiClass, psiAnnotation));
  }

  @NotNull
  Collection<PsiMethod> createToStringMethod(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    if (hasToStringMethodDefined(psiClass)) {
      return Collections.emptyList();
    }

    final Collection<MemberInfo> memberInfos = getEqualsAndHashCodeToStringHandler().filterFields(psiClass, psiAnnotation, false, INCLUDE_ANNOTATION_METHOD);
    final PsiMethod stringMethod = createToStringMethod(psiClass, memberInfos, psiAnnotation, false);
    return Collections.singletonList(stringMethod);
  }

  @NotNull
  public PsiMethod createToStringMethod(@NotNull PsiClass psiClass, @NotNull Collection<MemberInfo> memberInfos, @NotNull PsiAnnotation psiAnnotation, boolean forceCallSuper) {
    final PsiManager psiManager = psiClass.getManager();

    final String paramString = createParamString(psiClass, memberInfos, psiAnnotation, forceCallSuper);
    final String blockText = String.format("return \"%s(%s)\";", getSimpleClassName(psiClass), paramString);

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, TO_STRING_METHOD_NAME)
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

    final EqualsAndHashCodeToStringHandler handler = getEqualsAndHashCodeToStringHandler();
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
      if (getEqualsAndHashCodeToStringHandler().filterFields(containingClass, psiAnnotation, false, INCLUDE_ANNOTATION_METHOD).stream()
        .map(MemberInfo::getName).anyMatch(psiFieldName::equals)) {
        return LombokPsiElementUsage.READ;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
