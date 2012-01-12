package de.plushnikov.intellij.lombok.processor.clazz.constructor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.LombokConstants;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.clazz.AbstractLombokClassProcessor;
import de.plushnikov.intellij.lombok.util.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiElementUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;

/**
 * Base lombok processor class for constructor processing
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractConstructorClassProcessor extends AbstractLombokClassProcessor {

  protected AbstractConstructorClassProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (!validateAnnotationOnRigthType(psiClass, builder)) {
      result = false;
    }
    if (!validateVisibility(psiAnnotation)) {
      result = false;
    }
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String visibility = LombokProcessorUtil.getAccessVisibity(psiAnnotation);
    return null != visibility;
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
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

    final PsiMethod[] definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
    final String constructorName = psiClass.getName();

    if (containsMethod(definedConstructors, constructorName, paramTypes)) {
      if (paramTypes.isEmpty()) {
        builder.addError("Constructor without parameters is already defined");
      } else {
        builder.addError(String.format("Constructor with %d parameters is already defined", paramTypes.size()));
      }
      result = false;
    }

    if (isStaticConstructor(staticConstructorName)) {
      final PsiMethod[] definedMethods = PsiClassUtil.collectClassStaticMethodsIntern(psiClass);

      if (containsMethod(definedMethods, staticConstructorName, paramTypes)) {
        if (paramTypes.isEmpty()) {
          builder.addError(String.format("Method '%s' matched staticConstructorName is already defined", staticConstructorName));
        } else {
          builder.addError(String.format("Method '%s' with %d parameters matched staticConstructorName is already defined", staticConstructorName, paramTypes.size()));
        }
        result = false;
      }
    }

    return result;
  }

  private boolean containsMethod(final PsiMethod[] definedMethods, final String methodName, final List<PsiType> paramTypes) {
    for (PsiMethod method : definedMethods) {
      if (PsiElementUtil.methodMatches(method, null, null, methodName, paramTypes)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  protected Collection<PsiField> getAllNotInitializedAndNotStaticFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> allNotInitializedNotStaticFields = new ArrayList<PsiField>();
    for (PsiField psiField : psiClass.getFields()) {
      // skip fields named $
      boolean addField = !psiField.getName().startsWith(LombokConstants.LOMBOK_INTERN_FIELD_MARKER);

      final PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        // skip static fields
        addField &= !modifierList.hasModifierProperty(PsiModifier.STATIC);
        // skip initialized final fields
        addField &= !(null != psiField.getInitializer() && modifierList.hasModifierProperty(PsiModifier.FINAL));
      }

      if (addField) {
        allNotInitializedNotStaticFields.add(psiField);
      }
    }
    return allNotInitializedNotStaticFields;
  }

  @NotNull
  protected Collection<PsiMethod> createConstructorMethod(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation, @NotNull Collection<PsiField> params) {
    final String staticName = getStaticConstructorName(psiAnnotation);

    return createConstructorMethod(psiClass, methodVisibility, psiAnnotation, params, staticName);
  }

  protected String getStaticConstructorName(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "staticName", String.class);
  }

  protected boolean isStaticConstructor(@Nullable String staticName) {
    return !StringUtil.isEmptyOrSpaces(staticName);
  }

  @NotNull
  protected Collection<PsiMethod> createConstructorMethod(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation, @NotNull Collection<PsiField> params, @Nullable String staticName) {
    final String suppressConstructorProperties = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "suppressConstructorProperties", String.class);

    final boolean staticConstrRequired = isStaticConstructor(staticName);

    final String constrVisibility = staticConstrRequired || psiClass.isEnum() ? PsiModifier.PRIVATE : methodVisibility;

    PsiMethod constructor = createConstructor(psiClass, constrVisibility, Boolean.valueOf(suppressConstructorProperties), params, psiAnnotation);
    if (staticConstrRequired) {
      PsiMethod staticConstructor = createStaticConstructor(psiClass, staticName, params, psiAnnotation);
      return Arrays.asList(constructor, staticConstructor);
    }
    return Collections.singletonList(constructor);
  }

  private PsiMethod createConstructor(@NotNull PsiClass psiClass, @NotNull String methodVisibility, boolean suppressConstructorProperties, @NotNull Collection<PsiField> params, @NotNull PsiAnnotation psiAnnotation) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      if (!suppressConstructorProperties && !params.isEmpty()) {
        builder.append("@java.beans.ConstructorProperties( {");
        for (PsiField param : params) {
          builder.append('"').append(param.getName()).append('"').append(',');
        }
        builder.deleteCharAt(builder.length() - 1);
        builder.append("} ) ");
      }

      builder.append(methodVisibility);
      if (StringUtil.isNotEmpty(methodVisibility)) {
        builder.append(' ');
      }

      builder.append(psiClass.getName());
      appendParamDeclaration(params, builder);
      builder.append("{");
      builder.append("\nsuper();");
      appendParamInitialization(params, builder);
      builder.append("\n}");

      for (PsiField psiField : params) {
        UserMapKeys.addWriteUsageFor(psiField);
      }

      return PsiMethodUtil.createMethod(psiClass, builder.toString(), psiAnnotation);
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private PsiMethod createStaticConstructor(PsiClass psiClass, String staticName, Collection<PsiField> params, PsiAnnotation psiAnnotation) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(PsiModifier.PUBLIC);
      builder.append(' ');
      builder.append(PsiModifier.STATIC);
      builder.append(' ');

      builder.append(psiClass.getName());
      builder.append(' ');

      builder.append(staticName);
      appendParamDeclaration(params, builder);
      builder.append("{ ").
          append("return new ").append(psiClass.getName()).
          append("(");
      appendParamList(params, builder).
          append(")").
          append(";}");

      return PsiMethodUtil.createMethod(psiClass, builder.toString(), psiAnnotation);
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private StringBuilder appendParamDeclaration(Collection<PsiField> params, StringBuilder builder) {
    builder.append('(');
    if (!params.isEmpty()) {
      for (PsiField param : params) {
        builder.append(param.getType().getCanonicalText()).append(' ').append(param.getName()).append(',');
      }
      builder.deleteCharAt(builder.length() - 1);
    }
    builder.append(')');
    return builder;
  }

  private StringBuilder appendParamInitialization(Collection<PsiField> params, StringBuilder builder) {
    for (PsiField param : params) {
      builder.append("\nthis.").append(param.getName()).append(" = ").append(param.getName()).append(';');
    }
    return builder;
  }

  private StringBuilder appendParamList(Collection<PsiField> params, StringBuilder builder) {
    builder.append('(');
    if (!params.isEmpty()) {
      for (PsiField param : params) {
        builder.append(param.getName()).append(',');
      }
      builder.deleteCharAt(builder.length() - 1);
    }
    builder.append(')');
    return builder;
  }
}
