package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightReferenceListBuilder;
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiElementUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base lombok processor class for constructor processing
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractConstructorClassProcessor extends AbstractClassProcessor {
  private static final String BUILDER_DEFAULT_ANNOTATION = Builder.Default.class.getCanonicalName();

  AbstractConstructorClassProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass,
                                    @NotNull Class<? extends PsiElement> supportedClass) {
    super(supportedClass, supportedAnnotationClass);
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_CONSTRUCTOR_ENABLED);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
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

  private boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String visibility = LombokProcessorUtil.getAccessVisibility(psiAnnotation);
    return null != visibility;
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("Annotation is only supported on a class or enum type");
      result = false;
    }
    return result;
  }

  public boolean validateBaseClassConstructor(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    if (psiClass instanceof PsiAnonymousClass || psiClass.isEnum()) {
      return true;
    }
    PsiClass baseClass = psiClass.getSuperClass();
    if (baseClass == null) {
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
    builder.addError("Lombok needs a default constructor in the base class");
    return false;
  }

  private boolean validateIsStaticConstructorNotDefined(@NotNull PsiClass psiClass, @Nullable String staticConstructorName, @NotNull Collection<PsiField> params, @NotNull ProblemBuilder builder) {
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
          builder.addError(String.format("Method '%s' matched staticConstructorName is already defined", staticConstructorName), new SafeDeleteFix(existedStaticMethod));
        } else {
          builder.addError(String.format("Method '%s' with %d parameters matched staticConstructorName is already defined", staticConstructorName, paramTypes.size()), new SafeDeleteFix(existedStaticMethod));
        }
        result = false;
      }
    }

    return result;
  }

  public boolean validateIsConstructorNotDefined(@NotNull PsiClass psiClass, @Nullable String staticConstructorName, @NotNull Collection<PsiField> params, @NotNull ProblemBuilder builder) {
    // Constructor not defined or static constructor not defined
    return validateIsConstructorNotDefined(psiClass, params,builder) || validateIsStaticConstructorNotDefined(psiClass, staticConstructorName, params, builder);
  }

  private boolean validateIsConstructorNotDefined(@NotNull PsiClass psiClass, @NotNull Collection<PsiField> params, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final List<PsiType> paramTypes = new ArrayList<>(params.size());
    for (PsiField param : params) {
      paramTypes.add(param.getType());
    }

    final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
    final String constructorName = getConstructorName(psiClass);

    final PsiMethod existedMethod = findExistedMethod(definedConstructors, constructorName, paramTypes);
    if (null != existedMethod) {
      if (paramTypes.isEmpty()) {
        builder.addError("Constructor without parameters is already defined", new SafeDeleteFix(existedMethod));
      } else {
        builder.addError(String.format("Constructor with %d parameters is already defined", paramTypes.size()), new SafeDeleteFix(existedMethod));
      }
      result = false;
    }

    return result;
  }

  @NotNull
  public String getConstructorName(@NotNull PsiClass psiClass) {
    return StringUtil.notNullize(psiClass.getName());
  }

  @Nullable
  private PsiMethod findExistedMethod(final Collection<PsiMethod> definedMethods, final String methodName, final List<PsiType> paramTypes) {
    for (PsiMethod method : definedMethods) {
      if (PsiElementUtil.methodMatches(method, null, null, methodName, paramTypes)) {
        return method;
      }
    }
    return null;
  }

  @NotNull
  protected Collection<PsiField> getAllNotInitializedAndNotStaticFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> allNotInitializedNotStaticFields = new ArrayList<>();
    final boolean classAnnotatedWithValue = PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, Value.class);
    for (PsiField psiField : psiClass.getFields()) {
      // skip fields named $
      boolean addField = !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);

      final PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        // skip static fields
        addField &= !modifierList.hasModifierProperty(PsiModifier.STATIC);

        boolean isFinal = isFieldFinal(psiField, modifierList, classAnnotatedWithValue);
        // skip initialized final fields
        addField &= (!isFinal || null == psiField.getInitializer() ||
          PsiAnnotationSearchUtil.findAnnotation(psiField, BUILDER_DEFAULT_ANNOTATION) != null);
      }

      if (addField) {
        allNotInitializedNotStaticFields.add(psiField);
      }
    }
    return allNotInitializedNotStaticFields;
  }

  @NotNull
  public Collection<PsiField> getAllFields(@NotNull PsiClass psiClass) {
    return getAllNotInitializedAndNotStaticFields(psiClass);
  }

  @NotNull
  public Collection<PsiField> getRequiredFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> result = new ArrayList<>();
    final boolean classAnnotatedWithValue = PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, Value.class);

    for (PsiField psiField : getAllNotInitializedAndNotStaticFields(psiClass)) {
      final PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        final boolean isFinal = isFieldFinal(psiField, modifierList, classAnnotatedWithValue);
        final boolean isNonNull = PsiAnnotationSearchUtil.isAnnotatedWith(psiField, LombokUtils.NON_NULL_PATTERN);
        // accept initialized final or nonnull fields
        if ((isFinal || isNonNull) && null == psiField.getInitializer()) {
          result.add(psiField);
        }
      }
    }
    return result;
  }

  private boolean isFieldFinal(@NotNull PsiField psiField, @NotNull PsiModifierList modifierList, boolean classAnnotatedWithValue) {
    boolean isFinal = modifierList.hasModifierProperty(PsiModifier.FINAL);
    if (!isFinal && classAnnotatedWithValue) {
      isFinal = PsiAnnotationSearchUtil.isNotAnnotatedWith(psiField, NonFinal.class);
    }
    return isFinal;
  }

  @NotNull
  protected Collection<PsiMethod> createConstructorMethod(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String methodModifier, @NotNull PsiAnnotation psiAnnotation, boolean useJavaDefaults, @NotNull Collection<PsiField> params) {
    final String staticName = getStaticConstructorName(psiAnnotation);

    return createConstructorMethod(psiClass, methodModifier, psiAnnotation, useJavaDefaults, params, staticName);
  }

  protected String getStaticConstructorName(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticName");
  }

  private boolean isStaticConstructor(@Nullable String staticName) {
    return !StringUtil.isEmptyOrSpaces(staticName);
  }

  @NotNull
  protected Collection<PsiMethod> createConstructorMethod(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String methodModifier, @NotNull PsiAnnotation psiAnnotation, boolean useJavaDefaults, @NotNull Collection<PsiField> params, @Nullable String staticName) {
    List<PsiMethod> methods = new ArrayList<>();

    boolean hasStaticConstructor = !validateIsStaticConstructorNotDefined(psiClass, staticName, params, ProblemEmptyBuilder.getInstance());
    boolean hasConstructor = !validateIsConstructorNotDefined(psiClass, params, ProblemEmptyBuilder.getInstance());

    final boolean staticConstructorRequired = isStaticConstructor(staticName);

    final String constructorVisibility = staticConstructorRequired || psiClass.isEnum() ? PsiModifier.PRIVATE : methodModifier;

    if (!hasConstructor) {
      final PsiMethod constructor = createConstructor(psiClass, constructorVisibility, useJavaDefaults, params, psiAnnotation);
      methods.add(constructor);
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
      .withModifier(modifier);

    final List<String> fieldNames = new ArrayList<>();
    final AccessorsInfo classAccessorsInfo = AccessorsInfo.build(psiClass);
    for (PsiField psiField : params) {
      final AccessorsInfo paramAccessorsInfo = AccessorsInfo.build(psiField, classAccessorsInfo);
      fieldNames.add(paramAccessorsInfo.removePrefix(psiField.getName()));
    }

    if (!fieldNames.isEmpty()) {
      boolean addConstructorProperties = configDiscovery.getBooleanLombokConfigProperty(ConfigKey.ANYCONSTRUCTOR_ADD_CONSTRUCTOR_PROPERTIES, psiClass);
      if (addConstructorProperties || !configDiscovery.getBooleanLombokConfigProperty(ConfigKey.ANYCONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES, psiClass)) {
        final String constructorPropertiesAnnotation = "java.beans.ConstructorProperties( {" +
          fieldNames.stream().collect(Collectors.joining("\", \"", "\"", "\"")) +
          "} ) ";
        constructorBuilder.withAnnotation(constructorPropertiesAnnotation);
      }
    }

    constructorBuilder.withAnnotations(LombokProcessorUtil.getOnX(psiAnnotation, "onConstructor"));

    if (!useJavaDefaults) {
      final Iterator<String> fieldNameIterator = fieldNames.iterator();
      final Iterator<PsiField> fieldIterator = params.iterator();
      while (fieldNameIterator.hasNext() && fieldIterator.hasNext()) {
        constructorBuilder.withParameter(fieldNameIterator.next(), fieldIterator.next().getType());
      }
    }

    final StringBuilder blockText = new StringBuilder();

    final Iterator<String> fieldNameIterator = fieldNames.iterator();
    final Iterator<PsiField> fieldIterator = params.iterator();
    while (fieldNameIterator.hasNext() && fieldIterator.hasNext()) {
      final PsiField param = fieldIterator.next();
      final String fieldName = fieldNameIterator.next();
      final String fieldInitializer = useJavaDefaults ? PsiTypesUtil.getDefaultValueOfType(param.getType()) : fieldName;
      blockText.append(String.format("this.%s = %s;\n", param.getName(), fieldInitializer));
    }

    constructorBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(blockText.toString(), constructorBuilder));

    return constructorBuilder;
  }

  private PsiMethod createStaticConstructor(PsiClass psiClass, @PsiModifier.ModifierConstant String methodModifier, String staticName, boolean useJavaDefaults, Collection<PsiField> params, PsiAnnotation psiAnnotation) {
    LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiClass.getManager(), staticName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(methodModifier)
      .withModifier(PsiModifier.STATIC);

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
      for (PsiField param : params) {
        methodBuilder.withParameter(StringUtil.notNullize(param.getName()), substitutor.substitute(param.getType()));
      }
    }

    final String codeBlockText = createStaticCodeBlockText(returnType, useJavaDefaults, methodBuilder.getParameterList());
    methodBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(codeBlockText, methodBuilder));

    return methodBuilder;
  }

  @NotNull
  private LightTypeParameterBuilder createTypeParameter(LombokLightMethodBuilder method, int index, PsiTypeParameter psiClassTypeParameter) {
    final String nameOfTypeParameter = StringUtil.notNullize(psiClassTypeParameter.getName());

    final LightTypeParameterBuilder result = new LightTypeParameterBuilder(nameOfTypeParameter, method, index);
    final LightReferenceListBuilder resultExtendsList = result.getExtendsList();
    for (PsiClassType referencedType : psiClassTypeParameter.getExtendsList().getReferencedTypes()) {
      resultExtendsList.addReference(referencedType);
    }
    return result;
  }

  @NotNull
  private String createStaticCodeBlockText(@NotNull PsiType psiType, boolean useJavaDefaults, @NotNull final PsiParameterList parameterList) {
    final String psiClassName = psiType.getPresentableText();
    final String paramsText = useJavaDefaults ? "" : joinParameters(parameterList);
    return String.format("return new %s(%s);", psiClassName, paramsText);
  }

  private String joinParameters(PsiParameterList parameterList) {
    return Arrays.stream(parameterList.getParameters()).map(PsiParameter::getName).collect(Collectors.joining(","));
  }
}
