package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKeys;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
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
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Base lombok processor class for constructor processing
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractConstructorClassProcessor extends AbstractClassProcessor {

  AbstractConstructorClassProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
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

  private boolean validateBaseClassConstructor(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
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

  public boolean validateIsConstructorDefined(@NotNull PsiClass psiClass, @Nullable String staticConstructorName, @NotNull Collection<PsiField> params, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final List<PsiType> paramTypes = new ArrayList<PsiType>(params.size());
    for (PsiField param : params) {
      paramTypes.add(param.getType());
    }

    final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
    final String constructorName = getConstructorName(psiClass);

    if (containsMethod(definedConstructors, constructorName, paramTypes)) {
      if (paramTypes.isEmpty()) {
        builder.addError("Constructor without parameters is already defined");
      } else {
        builder.addError("Constructor with %d parameters is already defined", paramTypes.size());
      }
      result = false;
    }

    if (isStaticConstructor(staticConstructorName)) {
      final Collection<PsiMethod> definedMethods = PsiClassUtil.collectClassStaticMethodsIntern(psiClass);

      if (containsMethod(definedMethods, staticConstructorName, paramTypes)) {
        if (paramTypes.isEmpty()) {
          builder.addError("Method '%s' matched staticConstructorName is already defined", staticConstructorName);
        } else {
          builder.addError("Method '%s' with %d parameters matched staticConstructorName is already defined", staticConstructorName, paramTypes.size());
        }
        result = false;
      }
    }

    return result;
  }

  @NotNull
  public String getConstructorName(@NotNull PsiClass psiClass) {
    return psiClass.getName();
  }

  private boolean containsMethod(final Collection<PsiMethod> definedMethods, final String methodName, final List<PsiType> paramTypes) {
    for (PsiMethod method : definedMethods) {
      if (PsiElementUtil.methodMatches(method, null, null, methodName, paramTypes)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @SuppressWarnings("deprecation")
  protected Collection<PsiField> getAllNotInitializedAndNotStaticFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> allNotInitializedNotStaticFields = new ArrayList<PsiField>();
    final boolean classAnnotatedWithValue = PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, Value.class, lombok.experimental.Value.class);
    for (PsiField psiField : psiClass.getFields()) {
      // skip fields named $
      boolean addField = !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);

      final PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        // skip static fields
        addField &= !modifierList.hasModifierProperty(PsiModifier.STATIC);

        boolean isFinal = isFieldFinal(psiField, modifierList, classAnnotatedWithValue);
        // skip initialized final fields
        addField &= (!isFinal || null == psiField.getInitializer());
      }

      if (addField) {
        allNotInitializedNotStaticFields.add(psiField);
      }
    }
    return allNotInitializedNotStaticFields;
  }

  @NotNull
  @SuppressWarnings("deprecation")
  public Collection<PsiField> getRequiredFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> result = new ArrayList<PsiField>();
    final boolean classAnnotatedWithValue = PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, Value.class, lombok.experimental.Value.class);

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
    final boolean staticConstructorRequired = isStaticConstructor(staticName);

    final String constructorVisibility = staticConstructorRequired || psiClass.isEnum() ? PsiModifier.PRIVATE : methodModifier;

    final boolean suppressConstructorProperties = useJavaDefaults || readAnnotationOrConfigProperty(psiAnnotation, psiClass, "suppressConstructorProperties", ConfigKeys.ANYCONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES);

    final PsiMethod constructor = createConstructor(psiClass, constructorVisibility, suppressConstructorProperties, useJavaDefaults, params, psiAnnotation);
    if (staticConstructorRequired) {
      PsiMethod staticConstructor = createStaticConstructor(psiClass, staticName, useJavaDefaults, params, psiAnnotation);
      return Arrays.asList(constructor, staticConstructor);
    }
    return Collections.singletonList(constructor);
  }

  private PsiMethod createConstructor(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String modifier, boolean suppressConstructorProperties, boolean useJavaDefaults, @NotNull Collection<PsiField> params, @NotNull PsiAnnotation psiAnnotation) {
    LombokLightMethodBuilder constructor = new LombokLightMethodBuilder(psiClass.getManager(), getConstructorName(psiClass))
        .withConstructor(true)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(modifier);

    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiClass);
    final PsiModifierList modifierList = constructor.getModifierList();

    if (!suppressConstructorProperties && !useJavaDefaults && !params.isEmpty()) {
      StringBuilder constructorPropertiesAnnotation = new StringBuilder("java.beans.ConstructorProperties( {");
      for (PsiField param : params) {
        constructorPropertiesAnnotation.append('"').append(accessorsInfo.removePrefix(param.getName())).append('"').append(',');
      }
      constructorPropertiesAnnotation.deleteCharAt(constructorPropertiesAnnotation.length() - 1);
      constructorPropertiesAnnotation.append("} ) ");

      modifierList.addAnnotation(constructorPropertiesAnnotation.toString());
    }

    addOnXAnnotations(psiAnnotation, modifierList, "onConstructor");

    if (!useJavaDefaults) {
      for (PsiField param : params) {
        constructor.withParameter(accessorsInfo.removePrefix(param.getName()), param.getType());
      }
    }

    final StringBuilder blockText = new StringBuilder();
    for (PsiField param : params) {
      final String fieldInitializer = useJavaDefaults ? PsiTypesUtil.getDefaultValueOfType(param.getType()) : accessorsInfo.removePrefix(param.getName());
      blockText.append(String.format("this.%s = %s;\n", param.getName(), fieldInitializer));
    }
    constructor.withBody(PsiMethodUtil.createCodeBlockFromText(blockText.toString(), psiClass));

    return constructor;
  }

  private PsiMethod createStaticConstructor(PsiClass psiClass, String staticName, boolean useJavaDefaults, Collection<PsiField> params, PsiAnnotation psiAnnotation) {
    LombokLightMethodBuilder method = new LombokLightMethodBuilder(psiClass.getManager(), staticName)
        .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(psiClass))
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(PsiModifier.PUBLIC, PsiModifier.STATIC);

    //Static method need type parameter
    for (PsiTypeParameter typeParameter : psiClass.getTypeParameters()) {
      method.withTypeParameter(typeParameter);
    }

//    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
//    final PsiTypeParameter typeParameter = psiClass.getTypeParameters()[0];
//    final PsiClassType methodParameterType = factory.createType(typeParameter, PsiSubstitutor.EMPTY);
//    method.addParameter(new LightParameter("myParam1", methodParameterType, psiClass, psiClass.getLanguage()));

    if (!useJavaDefaults) {
      for (PsiField param : params) {
        final PsiType methodParameterType = param.getType();
        method.withParameter(param.getName(), methodParameterType);
      }
    }

    method.withBody(createStaticCodeBlock(psiClass, useJavaDefaults, method.getParameterList()));

    return method;
  }

  @NotNull
  private PsiCodeBlock createStaticCodeBlock(@NotNull PsiClass psiClass, boolean useJavaDefaults, @NotNull final PsiParameterList parameterList) {
    final String blockText;
    if (isShouldGenerateFullBodyBlock()) {
      final String psiClassName = buildClassNameWithGenericTypeParameters(psiClass);
      final String paramsText = useJavaDefaults ? "" : joinParameters(parameterList);
      blockText = String.format("return new %s(%s);", psiClassName, paramsText);
    } else {
      blockText = "return null;";
    }
    return PsiMethodUtil.createCodeBlockFromText(blockText, psiClass);
  }

  private String buildClassNameWithGenericTypeParameters(@NotNull final PsiClass psiClass) {
    StringBuilder psiClassName = new StringBuilder(getConstructorName(psiClass));

    final PsiTypeParameter[] psiClassTypeParameters = psiClass.getTypeParameters();
    if (psiClassTypeParameters.length > 0) {
      psiClassName.append('<');
      for (PsiTypeParameter psiClassTypeParameter : psiClassTypeParameters) {
        psiClassName.append(psiClassTypeParameter.getName()).append(',');
      }
      psiClassName.setCharAt(psiClassName.length() - 1, '>');
    }
    return psiClassName.toString();
  }

  private String joinParameters(PsiParameterList parameterList) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      for (PsiParameter psiParameter : parameterList.getParameters()) {
        builder.append(psiParameter.getName()).append(',');
      }
      if (parameterList.getParameters().length > 0) {
        builder.deleteCharAt(builder.length() - 1);
      }
      return builder.toString();
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

}
