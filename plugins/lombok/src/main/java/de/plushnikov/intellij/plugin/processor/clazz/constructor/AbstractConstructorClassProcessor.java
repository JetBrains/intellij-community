package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.RecordAugmentProvider;
import com.intellij.psi.impl.light.LightReferenceListBuilder;
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.problem.ProblemProcessingSink;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokAddNullAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base lombok processor class for constructor processing
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractConstructorClassProcessor extends AbstractClassProcessor {
  private static final String BUILDER_DEFAULT_ANNOTATION = LombokClassNames.BUILDER_DEFAULT;

  AbstractConstructorClassProcessor(@NotNull String supportedAnnotationClass,
                                    @NotNull Class<? extends PsiElement> supportedClass) {
    super(supportedClass, supportedAnnotationClass);
  }

  @Override
  public Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    return List.of(getConstructorName(psiClass), getStaticConstructorName(psiAnnotation));
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    boolean result = true;
    if (!validateAnnotationOnRightType(psiClass, builder)) {
      result = false;
    }
    if (!validateVisibility(psiAnnotation)) {
      result = false;
    }

    if (!validateBaseClassConstructor(psiClass, builder)) {
      result = false;
    }
    return result;
  }

  private static boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String visibility = LombokProcessorUtil.getAccessVisibility(psiAnnotation);
    return null != visibility;
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isRecord()) {
      builder.addErrorMessage("inspection.message.annotation.only.supported.on.class.or.enum.type",
                              StringUtil.getShortName(getSupportedAnnotationClasses()[0]))
        .withLocalQuickFixes(
          () -> {
            final var annotationFqn = Arrays.stream(getSupportedAnnotationClasses()).findFirst().orElse(null);
            return PsiQuickFixFactory.createDeleteAnnotationFix(psiClass, annotationFqn);
          }
        );
      result = false;
    }
    return result;
  }

  public boolean validateBaseClassConstructor(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    if (psiClass instanceof PsiAnonymousClass || psiClass.isEnum()) {
      return true;
    }
    PsiClass baseClass = psiClass.getSuperClass();
    if (baseClass == null || psiClass.getManager().areElementsEquivalent(psiClass, baseClass)) {
      return true;
    }
    PsiMethod[] constructors = baseClass.getConstructors();
    if (constructors.length == 0) {
      return true;
    }

    for (PsiMethod constructor : constructors) {
      final int parametersCount = constructor.getParameterList().getParametersCount();
      if (parametersCount == 0 || parametersCount == 1 && constructor.isVarArgs()) {
        return true;
      }
    }
    builder.addErrorMessage("inspection.message.lombok.needs.default.constructor.in.base.class");
    return false;
  }

  private static boolean validateIsStaticConstructorNotDefined(@NotNull PsiClass psiClass,
                                                               @Nullable String staticConstructorName,
                                                               @NotNull Collection<PsiField> params,
                                                               @NotNull ProblemSink builder) {
    boolean result = true;

    final List<PsiType> paramTypes = new ArrayList<>(params.size());
    for (PsiField param : params) {
      paramTypes.add(param.getType());
    }

    if (isStaticConstructor(staticConstructorName)) {
      final Collection<PsiMethod> definedMethods = PsiClassUtil.collectClassStaticMethodsIntern(psiClass);

      final PsiMethod existedStaticMethod = findExistedMethod(definedMethods, staticConstructorName, paramTypes);
      if (null != existedStaticMethod) {
        if (paramTypes.isEmpty()) {
          builder.addErrorMessage("inspection.message.method.s.matched.static.constructor.name.already.defined", staticConstructorName)
            .withLocalQuickFixes(() -> new SafeDeleteFix(existedStaticMethod));
        }
        else {
          builder.addErrorMessage("inspection.message.method.s.with.d.parameters.matched.static.constructor.name.already.defined",
                                  staticConstructorName, paramTypes.size())
            .withLocalQuickFixes(() -> new SafeDeleteFix(existedStaticMethod));
        }
        result = false;
      }
    }

    return result;
  }

  public boolean validateIsConstructorNotDefined(@NotNull PsiClass psiClass, @Nullable String staticConstructorName,
                                                 @NotNull Collection<PsiField> params, @NotNull ProblemSink builder) {
    // Constructor not defined or static constructor not defined
    return validateIsConstructorNotDefined(psiClass, params, builder) ||
           validateIsStaticConstructorNotDefined(psiClass, staticConstructorName, params, builder);
  }

  private boolean validateIsConstructorNotDefined(@NotNull PsiClass psiClass, @NotNull Collection<PsiField> params,
                                                  @NotNull ProblemSink builder) {
    boolean result = true;

    final List<PsiType> paramTypes = ContainerUtil.map(params, PsiField::getType);
    final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
    final String constructorName = getConstructorName(psiClass);

    final PsiMethod existedMethod = findExistedMethod(definedConstructors, constructorName, paramTypes);
    if (null != existedMethod) {
      if (paramTypes.isEmpty()) {
        builder.addErrorMessage("inspection.message.constructor.without.parameters.already.defined")
          .withLocalQuickFixes(
            () -> new SafeDeleteFix(existedMethod),
            () -> {
              final var annotationFqn = Arrays.stream(getSupportedAnnotationClasses()).findFirst().orElse(null);
              return PsiQuickFixFactory.createDeleteAnnotationFix(psiClass, annotationFqn);
            }
          );
      }
      else {
        final String name = HighlightMessageUtil.getSymbolName(existedMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_TYPE);
        builder.addErrorMessage("inspection.message.constructor.with.d.parameters.already.defined", name)
          .withLocalQuickFixes(
            () -> new SafeDeleteFix(existedMethod),
            () -> {
              final var annotationFqn = Arrays.stream(getSupportedAnnotationClasses()).findFirst().orElse(null);
              return PsiQuickFixFactory.createDeleteAnnotationFix(psiClass, annotationFqn);
            }
          );
      }
      result = false;
    }

    return result;
  }

  public @NotNull String getConstructorName(@NotNull PsiClass psiClass) {
    return StringUtil.notNullize(psiClass.getName());
  }

  private static @Nullable PsiMethod findExistedMethod(final Collection<PsiMethod> definedMethods,
                                                       final String methodName,
                                                       final List<PsiType> paramTypes) {
    for (PsiMethod method : definedMethods) {
      if (PsiElementUtil.methodMatches(method, null, null, methodName, paramTypes)) {
        return method;
      }
    }
    return null;
  }

  protected static @NotNull Collection<PsiField> getAllNotInitializedAndNotStaticFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> allNotInitializedNotStaticFields = new ArrayList<>();
    final boolean classAnnotatedWithValue = PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.VALUE);
    Collection<PsiField> fields = psiClass.isRecord() ? RecordAugmentProvider.getFieldAugments(psiClass)
                                                      : PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : fields) {
      if (isNotInitializedAndNotStaticField(psiField, classAnnotatedWithValue)) {
        allNotInitializedNotStaticFields.add(psiField);
      }
    }
    return allNotInitializedNotStaticFields;
  }

  static boolean isNotInitializedAndNotStaticField(@NotNull PsiField psiField, boolean classAnnotatedWithValue) {
    // skip fields named $
    boolean addField = !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);

    final PsiModifierList modifierList = psiField.getModifierList();
    if (null != modifierList) {
      // skip static fields
      addField &= !modifierList.hasModifierProperty(PsiModifier.STATIC);

      boolean isFinal = isFieldFinal(psiField, modifierList, classAnnotatedWithValue);
      // skip initialized final fields
      addField &= (!isFinal || !psiField.hasInitializer() ||
                   PsiAnnotationSearchUtil.findAnnotation(psiField, BUILDER_DEFAULT_ANNOTATION) != null);
    }
    return addField;
  }

  public static @NotNull Collection<PsiField> getAllFields(@NotNull PsiClass psiClass) {
    return getAllNotInitializedAndNotStaticFields(psiClass);
  }

  public @NotNull Collection<PsiField> getRequiredFields(@NotNull PsiClass psiClass) {
    return getRequiredFields(psiClass, false);
  }

  @NotNull
  Collection<PsiField> getRequiredFields(@NotNull PsiClass psiClass, boolean ignoreNonNull) {
    Collection<PsiField> result = new ArrayList<>();
    final boolean classAnnotatedWithValue = PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.VALUE);

    for (PsiField psiField : getAllNotInitializedAndNotStaticFields(psiClass)) {
      if (isRequiredField(psiField, ignoreNonNull, classAnnotatedWithValue)) {
        result.add(psiField);
      }
    }
    return result;
  }

  static boolean isRequiredField(@NotNull PsiField psiField, boolean ignoreNonNull, boolean classAnnotatedWithValue) {
    final PsiModifierList modifierList = psiField.getModifierList();
    boolean shouldAddField = false;
    if (null != modifierList) {
      final boolean isFinal = isFieldFinal(psiField, modifierList, classAnnotatedWithValue);
      final boolean isNonNull = !ignoreNonNull && PsiAnnotationSearchUtil.isAnnotatedWith(psiField, LombokUtils.NONNULL_ANNOTATIONS);
      // accept initialized final or nonnull fields
      shouldAddField = (isFinal || isNonNull) && !psiField.hasInitializer();
    }
    return shouldAddField;
  }

  private static boolean isFieldFinal(@NotNull PsiField psiField, @NotNull PsiModifierList modifierList, boolean classAnnotatedWithValue) {
    boolean isFinal = modifierList.hasModifierProperty(PsiModifier.FINAL);
    if (!isFinal && classAnnotatedWithValue) {
      isFinal = PsiAnnotationSearchUtil.isNotAnnotatedWith(psiField, LombokClassNames.NON_FINAL);
    }
    return isFinal;
  }

  protected @NotNull Collection<PsiMethod> createConstructorMethod(@NotNull PsiClass psiClass,
                                                                   @PsiModifier.ModifierConstant @NotNull String methodModifier,
                                                                   @NotNull PsiAnnotation psiAnnotation,
                                                                   boolean useJavaDefaults,
                                                                   @NotNull Collection<PsiField> params) {
    final String staticName = getStaticConstructorName(psiAnnotation);

    return createConstructorMethod(psiClass, methodModifier, psiAnnotation, useJavaDefaults, params, staticName, false);
  }

  protected String getStaticConstructorName(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticName", "");
  }

  private static boolean isStaticConstructor(@Nullable String staticName) {
    return !StringUtil.isEmptyOrSpaces(staticName);
  }

  protected @NotNull Collection<PsiMethod> createConstructorMethod(@NotNull PsiClass psiClass,
                                                                   @PsiModifier.ModifierConstant @NotNull String methodModifier,
                                                                   @NotNull PsiAnnotation psiAnnotation, boolean useJavaDefaults,
                                                                   @NotNull Collection<PsiField> params, @Nullable String staticName,
                                                                   boolean skipConstructorIfAnyConstructorExists) {
    List<PsiMethod> methods = new ArrayList<>();

    boolean hasStaticConstructor = !validateIsStaticConstructorNotDefined(psiClass, staticName, params, new ProblemProcessingSink());

    final boolean staticConstructorRequired = isStaticConstructor(staticName);

    final String constructorVisibility = staticConstructorRequired || psiClass.isEnum() ? PsiModifier.PRIVATE : methodModifier;

    // check if we should skip verification for presence of any (not Tolerated) constructors
    if (!skipConstructorIfAnyConstructorExists || !isAnyConstructorDefined(psiClass)) {
      boolean hasConstructor = !validateIsConstructorNotDefined(psiClass,
                                                                useJavaDefaults ? Collections.emptyList() : params,
                                                                new ProblemProcessingSink());
      if (!hasConstructor) {
        final PsiMethod constructor = createConstructor(psiClass, constructorVisibility, useJavaDefaults, params, psiAnnotation);
        methods.add(constructor);
      }
    }

    if (staticConstructorRequired && !hasStaticConstructor) {
      PsiMethod staticConstructor = createStaticConstructor(psiClass, methodModifier, staticName, useJavaDefaults, params, psiAnnotation);
      methods.add(staticConstructor);
    }

    return methods;
  }

  private PsiMethod createConstructor(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String modifier,
                                      boolean useJavaDefaults, @NotNull Collection<PsiField> params, @NotNull PsiAnnotation psiAnnotation) {
    LombokLightMethodBuilder constructorBuilder = new LombokLightMethodBuilder(psiClass.getManager(), getConstructorName(psiClass))
      .withConstructor(true)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(modifier)
      .withWriteAccess();

    LombokCopyableAnnotations.copyOnXAnnotations(psiAnnotation, constructorBuilder.getModifierList(), "onConstructor");

    if (useJavaDefaults) {
      final StringBuilder blockText = new StringBuilder();

      for (PsiField param : params) {
        final String fieldInitializer = PsiTypesUtil.getDefaultValueOfType(param.getType());
        blockText.append(String.format("this.%s = %s;\n", param.getName(), fieldInitializer));
      }
      constructorBuilder.withBodyText(blockText.toString());
    }
    else {
      final List<String> fieldNames = new ArrayList<>();
      final AccessorsInfo.AccessorsValues classAccessorsValues = AccessorsInfo.getAccessorsValues(psiClass);
      for (PsiField psiField : params) {
        final AccessorsInfo paramAccessorsInfo = AccessorsInfo.buildFor(psiField, classAccessorsValues);
        fieldNames.add(paramAccessorsInfo.removePrefixWithDefault(psiField.getName()));
      }

      if (!fieldNames.isEmpty()) {
        boolean addConstructorProperties =
          configDiscovery.getBooleanLombokConfigProperty(ConfigKey.ANYCONSTRUCTOR_ADD_CONSTRUCTOR_PROPERTIES, psiClass);
        if (addConstructorProperties ||
            !configDiscovery.getBooleanLombokConfigProperty(ConfigKey.ANYCONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES, psiClass)) {
          final String constructorPropertiesAnnotation = "java.beans.ConstructorProperties( {" +
                                                         fieldNames.stream().collect(Collectors.joining("\", \"", "\"", "\"")) +
                                                         "} ) ";
          constructorBuilder.withAnnotation(constructorPropertiesAnnotation);
        }
      }

      final StringBuilder blockText = new StringBuilder();

      final Iterator<String> fieldNameIterator = fieldNames.iterator();
      final Iterator<PsiField> fieldIterator = params.iterator();
      while (fieldNameIterator.hasNext() && fieldIterator.hasNext()) {
        final String parameterName = fieldNameIterator.next();
        final PsiField parameterField = fieldIterator.next();

        final LombokLightParameter parameter = new LombokLightParameter(parameterName, parameterField.getType(), constructorBuilder);
        parameter.setNavigationElement(parameterField);
        constructorBuilder.withParameter(parameter);
        LombokCopyableAnnotations.copyCopyableAnnotations(parameterField, parameter.getModifierList(),
                                                          LombokCopyableAnnotations.BASE_COPYABLE);

        blockText.append(String.format("this.%s = %s;\n", parameterField.getName(), parameterName));
      }

      constructorBuilder.withBodyText(blockText.toString());
    }

    return constructorBuilder;
  }

  private static PsiMethod createStaticConstructor(PsiClass psiClass,
                                                   @PsiModifier.ModifierConstant String methodModifier,
                                                   String staticName,
                                                   boolean useJavaDefaults,
                                                   Collection<PsiField> params,
                                                   PsiAnnotation psiAnnotation) {
    LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiClass.getManager(), staticName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(methodModifier)
      .withModifier(PsiModifier.STATIC)
      .withWriteAccess();

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (psiClass.hasTypeParameters()) {
      final PsiTypeParameter[] classTypeParameters = psiClass.getTypeParameters();

      // need to create new type parameters
      for (int index = 0; index < classTypeParameters.length; index++) {
        final PsiTypeParameter classTypeParameter = classTypeParameters[index];
        final LightTypeParameterBuilder methodTypeParameter = createTypeParameter(methodBuilder, index, classTypeParameter);
        methodBuilder.withTypeParameter(methodTypeParameter);

        substitutor = substitutor.put(classTypeParameter, PsiSubstitutor.EMPTY.substitute(methodTypeParameter));
      }
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    final PsiType returnType = factory.createType(psiClass, substitutor);
    methodBuilder.withMethodReturnType(returnType);

    if (!useJavaDefaults) {
      for (PsiField psiField : params) {
        final String parameterName = psiField.getName();
        final PsiType parameterType = substitutor.substitute(psiField.getType());
        final LombokLightParameter parameter = new LombokLightParameter(parameterName, parameterType, methodBuilder);
        parameter.setNavigationElement(psiField);
        methodBuilder.withParameter(parameter);
        LombokCopyableAnnotations.copyCopyableAnnotations(psiField, parameter.getModifierList(), LombokCopyableAnnotations.BASE_COPYABLE);
      }
    }

    final String codeBlockText = createStaticCodeBlockText(returnType, useJavaDefaults, methodBuilder.getParameterList());
    methodBuilder.withBodyText(codeBlockText);

    LombokAddNullAnnotations.createRelevantNonNullAnnotation(psiClass, methodBuilder);

    return methodBuilder;
  }

  private static @NotNull LightTypeParameterBuilder createTypeParameter(LombokLightMethodBuilder method,
                                                                        int index,
                                                                        PsiTypeParameter psiClassTypeParameter) {
    final String nameOfTypeParameter = StringUtil.notNullize(psiClassTypeParameter.getName());

    final LightTypeParameterBuilder result = new LightTypeParameterBuilder(nameOfTypeParameter, method, index);
    final LightReferenceListBuilder resultExtendsList = result.getExtendsList();
    for (PsiClassType referencedType : psiClassTypeParameter.getExtendsList().getReferencedTypes()) {
      resultExtendsList.addReference(referencedType);
    }
    return result;
  }

  private static @NotNull String createStaticCodeBlockText(@NotNull PsiType psiType,
                                                           boolean useJavaDefaults,
                                                           final @NotNull PsiParameterList parameterList) {
    final String psiClassName = psiType.getPresentableText();
    final String paramsText = useJavaDefaults ? "" : joinParameters(parameterList);
    return String.format("return new %s(%s);", psiClassName, paramsText);
  }

  private static String joinParameters(PsiParameterList parameterList) {
    return Arrays.stream(parameterList.getParameters()).map(PsiParameter::getName).collect(Collectors.joining(","));
  }
}
