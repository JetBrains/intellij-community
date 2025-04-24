package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.handler.EqualsAndHashCodeToStringHandler;
import de.plushnikov.intellij.plugin.processor.handler.EqualsAndHashCodeToStringHandler.MemberInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokAddNullAnnotations;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static de.plushnikov.intellij.plugin.LombokClassNames.*;

/**
 * Inspect and validate @ToString lombok annotation on a class
 * Creates toString() method for fields of this class
 *
 * @author Plushnikov Michail
 */
public final class ToStringProcessor extends AbstractClassProcessor {
  public static final String TO_STRING_METHOD_NAME = "toString";
  private static final List<String> METHOD_LIST = List.of(TO_STRING_METHOD_NAME);

  private static final String INCLUDE_ANNOTATION_METHOD = "name";
  private static final String INCLUDE_ANNOTATION_RANK = "rank";
  private static final String INCLUDE_ANNOTATION_SKIP_NULL = "skipNull";

  public ToStringProcessor() {
    super(PsiMethod.class, LombokClassNames.TO_STRING);
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    return METHOD_LIST;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink problemSink) {
    validateAnnotationOnRightType(psiClass, problemSink);
    if (problemSink.success()) {
      validateExistingMethods(psiClass, problemSink);
    }

    if (problemSink.deepValidation()) {
      final Collection<String> excludeProperty = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "exclude", String.class, List.of());
      final Collection<String> ofProperty = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "of", String.class, List.of());

      if (!excludeProperty.isEmpty() && !ofProperty.isEmpty()) {
        problemSink.addWarningMessage("inspection.message.exclude.are.mutually.exclusive.exclude.parameter.will.be.ignored")
          .withLocalQuickFixes(() -> PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "exclude", null));
      }
      else {
        validateExcludeParam(psiClass, problemSink, psiAnnotation, excludeProperty);
      }
      validateOfParam(psiClass, problemSink, psiAnnotation, ofProperty);
    }
    return problemSink.success();
  }

  private static void validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isRecord()) {
      builder.addErrorMessage("inspection.message.to.string.only.supported.on.class.or.enum.type");
      builder.markFailed();
    }
  }

  private static void validateExistingMethods(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    final boolean methodAlreadyExists = hasToStringMethodDefined(psiClass);
    if (methodAlreadyExists) {
      builder.addWarningMessage("inspection.message.not.generated.s.method.with.same.name.already.exists", TO_STRING_METHOD_NAME)
        .withLocalQuickFixes(() -> PsiQuickFixFactory.createDeleteAnnotationFix(psiClass, TO_STRING));
      builder.markFailed();
    }
  }

  private static boolean hasToStringMethodDefined(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    return PsiMethodUtil.hasMethodByName(classMethods, TO_STRING_METHOD_NAME, 0);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target, @Nullable String nameHint) {
    target.addAll(createToStringMethod(psiClass, psiAnnotation));
  }

  @NotNull Collection<PsiMethod> createToStringMethod(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    if (hasToStringMethodDefined(psiClass)) {
      return Collections.emptyList();
    }

    final Collection<MemberInfo> memberInfos = EqualsAndHashCodeToStringHandler.filterMembers(psiClass, psiAnnotation, false,
                                                                                              INCLUDE_ANNOTATION_METHOD,
                                                                                              ConfigKey.TOSTRING_ONLY_EXPLICITLY_INCLUDED,
                                                                                              TO_STRING_INCLUDE, TO_STRING_EXCLUDE);
    final PsiMethod stringMethod = createToStringMethod(psiClass, memberInfos, psiAnnotation, false);
    return Collections.singletonList(stringMethod);
  }

  public @NotNull PsiMethod createToStringMethod(@NotNull PsiClass psiClass, @NotNull Collection<MemberInfo> memberInfos,
                                                 @NotNull PsiAnnotation psiAnnotation, boolean forceCallSuper) {
    final PsiManager psiManager = psiClass.getManager();

    final String paramString = createParamString(psiClass, memberInfos, psiAnnotation, forceCallSuper);
    final String blockText = String.format("return \"%s(%s)\";", getSimpleClassName(psiClass), paramString);

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, TO_STRING_METHOD_NAME)
      .withMethodReturnType(PsiType.getJavaLangString(psiManager, GlobalSearchScope.allScope(psiClass.getProject())))
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PUBLIC)
      .withBodyText(blockText);

    LombokAddNullAnnotations.createRelevantNonNullAnnotation(psiClass, methodBuilder);

    return methodBuilder;
  }

  private static String getSimpleClassName(@NotNull PsiClass psiClass) {
    final StringBuilder psiClassName = new StringBuilder();

    PsiClass containingClass = psiClass;
    do {
      if (!psiClassName.isEmpty()) {
        psiClassName.insert(0, '.');
      }
      psiClassName.insert(0, containingClass.getName());
      containingClass = containingClass.getContainingClass();
    }
    while (null != containingClass);

    return psiClassName.toString();
  }

  private String createParamString(@NotNull PsiClass psiClass,
                                   @NotNull Collection<MemberInfo> memberInfos,
                                   @NotNull PsiAnnotation psiAnnotation,
                                   boolean forceCallSuper) {
    final boolean callSuper =
      forceCallSuper || readCallSuperAnnotationOrConfigProperty(psiAnnotation, psiClass, ConfigKey.TOSTRING_CALL_SUPER);
    final boolean doNotUseGetters =
      readAnnotationOrConfigProperty(psiAnnotation, psiClass, "doNotUseGetters", ConfigKey.TOSTRING_DO_NOT_USE_GETTERS);
    final boolean includeFieldNames =
      readAnnotationOrConfigProperty(psiAnnotation, psiClass, "includeFieldNames", ConfigKey.TOSTRING_INCLUDE_FIELD_NAMES);

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
        final PsiType componentType = ((PsiArrayType)classFieldType).getComponentType();
        if (componentType instanceof PsiPrimitiveType) {
          paramString.append("java.util.Arrays.toString(");
        }
        else {
          paramString.append("java.util.Arrays.deepToString(");
        }
      }

      final String memberAccessor = EqualsAndHashCodeToStringHandler.getMemberAccessorName(memberInfo, doNotUseGetters, psiClass);
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

  @Override
  public @NotNull Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addFieldsAnnotation(result, psiClass, TO_STRING_INCLUDE, TO_STRING_EXCLUDE);
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      final String psiFieldName = psiField.getName();
      final Collection<MemberInfo> memberInfos =
        EqualsAndHashCodeToStringHandler.filterMembers(containingClass, psiAnnotation, false,
                                                       INCLUDE_ANNOTATION_METHOD, ConfigKey.TOSTRING_ONLY_EXPLICITLY_INCLUDED,
                                                       TO_STRING_INCLUDE, TO_STRING_EXCLUDE);
      if (memberInfos.stream().filter(MemberInfo::isField).map(MemberInfo::getName).anyMatch(psiFieldName::equals)) {
        return LombokPsiElementUsage.READ;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
