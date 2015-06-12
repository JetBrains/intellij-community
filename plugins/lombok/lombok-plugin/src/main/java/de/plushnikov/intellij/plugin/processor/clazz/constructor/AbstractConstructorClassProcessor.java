package de.plushnikov.intellij.plugin.processor.clazz.constructor;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKeys;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
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

  protected AbstractConstructorClassProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
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
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String visibility = LombokProcessorUtil.getAccessVisibility(psiAnnotation);
    return null != visibility;
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("Annotation is only supported on a class or enum type");
      result = false;
    }
    return result;
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
    final boolean classAnnotatedWithValue = PsiAnnotationUtil.isAnnotatedWith(psiClass, Value.class, lombok.experimental.Value.class);
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

  protected boolean isFieldFinal(@NotNull PsiField psiField, @NotNull PsiModifierList modifierList, boolean classAnnotatedWithValue) {
    boolean isFinal = modifierList.hasModifierProperty(PsiModifier.FINAL);
    if (!isFinal && classAnnotatedWithValue) {
      isFinal = PsiAnnotationUtil.isNotAnnotatedWith(psiField, NonFinal.class);
    }
    return isFinal;
  }

  @NotNull
  protected Collection<PsiMethod> createConstructorMethod(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String methodModifier, @NotNull PsiAnnotation psiAnnotation, @NotNull Collection<PsiField> params) {
    final String staticName = getStaticConstructorName(psiAnnotation);

    return createConstructorMethod(psiClass, methodModifier, psiAnnotation, params, staticName);
  }

  protected String getStaticConstructorName(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticName");
  }

  protected boolean isStaticConstructor(@Nullable String staticName) {
    return !StringUtil.isEmptyOrSpaces(staticName);
  }

  @NotNull
  protected Collection<PsiMethod> createConstructorMethod(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String methodModifier, @NotNull PsiAnnotation psiAnnotation, @NotNull Collection<PsiField> params, @Nullable String staticName) {
    final boolean staticConstructorRequired = isStaticConstructor(staticName);

    final String constructorVisibility = staticConstructorRequired || psiClass.isEnum() ? PsiModifier.PRIVATE : methodModifier;

    final boolean suppressConstructorProperties = readAnnotationOrConfigProperty(psiAnnotation, psiClass, "suppressConstructorProperties", ConfigKeys.ANYCONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES);

    final PsiMethod constructor = createConstructor(psiClass, constructorVisibility, suppressConstructorProperties, params, psiAnnotation);
    if (staticConstructorRequired) {
      PsiMethod staticConstructor = createStaticConstructor(psiClass, staticName, params, psiAnnotation);
      return Arrays.asList(constructor, staticConstructor);
    }
    return Collections.singletonList(constructor);
  }

  private PsiMethod createConstructor(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String modifier, boolean suppressConstructorProperties, @NotNull Collection<PsiField> params, @NotNull PsiAnnotation psiAnnotation) {
    LombokLightMethodBuilder constructor = new LombokLightMethodBuilder(psiClass.getManager(), getConstructorName(psiClass))
        .withConstructor(true)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(modifier);

    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiClass);
    final PsiModifierList modifierList = constructor.getModifierList();

    if (!suppressConstructorProperties && !params.isEmpty()) {
      StringBuilder constructorPropertiesAnnotation = new StringBuilder("java.beans.ConstructorProperties( {");
      for (PsiField param : params) {
        constructorPropertiesAnnotation.append('"').append(accessorsInfo.removePrefix(param.getName())).append('"').append(',');
      }
      constructorPropertiesAnnotation.deleteCharAt(constructorPropertiesAnnotation.length() - 1);
      constructorPropertiesAnnotation.append("} ) ");

      modifierList.addAnnotation(constructorPropertiesAnnotation.toString());
    }

    addOnXAnnotations(psiAnnotation, modifierList, "onConstructor");

    for (PsiField param : params) {
      constructor.withParameter(accessorsInfo.removePrefix(param.getName()), param.getType());
    }

    final StringBuilder blockText = new StringBuilder();
    for (PsiField param : params) {
      blockText.append(String.format("this.%s = %s;\n", param.getName(), accessorsInfo.removePrefix(param.getName())));
    }
    constructor.withBody(PsiMethodUtil.createCodeBlockFromText(blockText.toString(), psiClass));

    return constructor;
  }

  private PsiMethod createStaticConstructor(PsiClass psiClass, String staticName, Collection<PsiField> params, PsiAnnotation psiAnnotation) {
    LombokLightMethodBuilder method = new LombokLightMethodBuilder(psiClass.getManager(), staticName)
        .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(psiClass))
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(PsiModifier.PUBLIC, PsiModifier.STATIC);

    for (PsiField param : params) {
      method.withParameter(param.getName(), param.getType());
    }

    final String paramsText = joinParameters(method.getParameterList());
    final String psiClassName = buildClassNameWithGenericTypeParameters(psiClass);
    final String blockText = String.format("return new %s(%s);", psiClassName, paramsText);
    method.withBody(PsiMethodUtil.createCodeBlockFromText(blockText, psiClass));

    return method;
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
