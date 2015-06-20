package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.handler.singular.AbstractSingularHandler;
import de.plushnikov.intellij.plugin.processor.handler.singular.SingularHandlerFactory;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.ErrorMessages;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Wither;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handler methods for Builder-processing
 *
 * @author Tomasz Kalkosiński
 * @author Michail Plushnikov
 */
public class BuilderHandler {
  private final static String ANNOTATION_BUILDER_CLASS_NAME = "builderClassName";
  public static final String ANNOTATION_BUILD_METHOD_NAME = "buildMethodName";
  public static final String ANNOTATION_BUILDER_METHOD_NAME = "builderMethodName";

  private final static String BUILDER_CLASS_NAME = "Builder";
  private final static String BUILD_METHOD_NAME = "build";
  private final static String BUILDER_METHOD_NAME = "builder";

  @SuppressWarnings("deprecation")
  private static final Collection<String> INVALID_ON_BUILDERS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
      Getter.class.getSimpleName(), Setter.class.getSimpleName(), Wither.class.getSimpleName(), ToString.class.getSimpleName(), EqualsAndHashCode.class.getSimpleName(),
      RequiredArgsConstructor.class.getSimpleName(), AllArgsConstructor.class.getSimpleName(), NoArgsConstructor.class.getSimpleName(),
      Data.class.getSimpleName(), Value.class.getSimpleName(), lombok.experimental.Value.class.getSimpleName(), FieldDefaults.class.getSimpleName())));


  private final ToStringProcessor toStringProcessor = new ToStringProcessor();
  private final NoArgsConstructorProcessor noArgsConstructorProcessor = new NoArgsConstructorProcessor();

  public boolean validate(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder problemBuilder) {
    boolean result = validateAnnotationOnRightType(psiClass, problemBuilder);
    if (result) {
      final PsiType psiBuilderType = getBuilderType(psiClass);
      final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);
      result = validateBuilderClassName(builderClassName, psiAnnotation.getProject(), problemBuilder) &&
          validateExistingBuilderClass(builderClassName, psiClass, problemBuilder);
    }
    return result;
  }

  protected boolean validateBuilderClassName(@NotNull String builderClassName, @NotNull Project project, @NotNull ProblemBuilder builder) {
    final PsiNameHelper psiNameHelper = PsiNameHelper.getInstance(project);
    if (!psiNameHelper.isIdentifier(builderClassName)) {
      builder.addError("%s ist not a valid identifier", builderClassName);
      return false;
    }
    return true;
  }

  protected boolean validateExistingBuilderClass(@NotNull String builderClassName, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    for (PsiClass psiInnerClass : PsiClassUtil.collectInnerClassesIntern(psiClass)) {
      if (builderClassName.equals(psiInnerClass.getName())) {
        if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiInnerClass, INVALID_ON_BUILDERS)) {
          builder.addError("Lombok annotations are not allowed on builder class.");
          return false;
        }
      }
    }
    return true;
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(ErrorMessages.canBeUsedOnClassOnly(Builder.class));
      return false;
    }
    return true;
  }

  public boolean validate(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder problemBuilder) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    boolean result = null != psiClass;
    if (result) {
      result = validateAnnotationOnRightType(psiMethod, problemBuilder);
      if (result) {
        final PsiType psiBuilderType = getBuilderType(psiClass, psiMethod);
        final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);
        result = validateBuilderClassName(builderClassName, psiAnnotation.getProject(), problemBuilder) &&
            validateExistingBuilderClass(builderClassName, psiClass, problemBuilder);
      }
    }
    return result;
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) && !psiMethod.isConstructor()) {
      builder.addError("%s is only supported on types, constructors, and static methods", Builder.class);
      return false;
    }
    return true;
  }

  public boolean existInnerClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    return existInnerClass(psiClass, null, psiAnnotation);
  }

  public boolean existInnerClass(@NotNull PsiClass psiClass, @Nullable PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    final PsiType psiBuilderType = getBuilderType(psiClass, psiMethod);
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);
    final PsiClass innerBuilderClass = PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);
    return null == innerBuilderClass;
  }

  public PsiType getBuilderType(@NotNull PsiClass psiClass) {
    return getBuilderType(psiClass, null);
  }

  public PsiType getBuilderType(@NotNull PsiClass psiClass, @Nullable PsiMethod psiMethod) {
    final PsiType psiBuilderTargetClass;
    if (null == psiMethod || psiMethod.isConstructor()) {
      psiBuilderTargetClass = PsiClassUtil.getTypeWithGenerics(psiClass);
    } else {
      psiBuilderTargetClass = psiMethod.getReturnType();
    }
    return psiBuilderTargetClass;
  }

  @NotNull
  public static String getBuildMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String buildMethodName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_BUILD_METHOD_NAME);
    return StringUtil.isEmptyOrSpaces(buildMethodName) ? BUILD_METHOD_NAME : buildMethodName;
  }

  @NotNull
  public static String getBuilderMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String builderMethodName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_METHOD_NAME);
    return StringUtil.isEmptyOrSpaces(builderMethodName) ? BUILDER_METHOD_NAME : builderMethodName;
  }

  @NotNull
  public String getBuilderClassName(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiType psiBuilderType) {
    String builderClassName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_CLASS_NAME);
    if (StringUtil.isEmptyOrSpaces(builderClassName)) {
      if (PsiType.VOID.equals(psiBuilderType)) {
        return StringUtil.capitalize(PsiType.VOID.getCanonicalText()) + BUILDER_CLASS_NAME;
      } else {
        PsiClass psiBuilderClass = PsiTypesUtil.getPsiClass(psiBuilderType);
        psiBuilderClass = null == psiBuilderClass ? psiClass : psiBuilderClass;
        return StringUtil.capitalize(psiBuilderClass.getName()) + BUILDER_CLASS_NAME;
      }
    }
    return builderClassName;
  }

  @NotNull
  public PsiMethod createBuilderMethod(@NotNull PsiClass containingClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass builderPsiClass, @NotNull PsiAnnotation psiAnnotation) {
    final PsiType psiTypeWithGenerics = PsiClassUtil.getTypeWithGenerics(builderPsiClass);
    final LombokLightMethodBuilder method = new LombokLightMethodBuilder(containingClass.getManager(), getBuilderMethodName(psiAnnotation))
        .withMethodReturnType(psiTypeWithGenerics)
        .withContainingClass(containingClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(PsiModifier.PUBLIC, PsiModifier.STATIC);

    addTypeParameters(builderPsiClass, psiMethod, method);

    method.withBody(PsiMethodUtil.createCodeBlockFromText(String.format("return new %s();", psiTypeWithGenerics.getPresentableText()), containingClass));
    return method;
  }

  @NotNull
  public PsiClass createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    final PsiType psiBuilderType = getBuilderType(psiClass, psiMethod);

    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);

    LombokLightClassBuilder builderClass = createBuilderClass(psiClass, psiMethod, builderClassName, psiAnnotation);
    builderClass.withConstructors(createConstructors(builderClass, psiAnnotation));

    final Collection<PsiField> builderFields = createFields(psiMethod, builderClass);
    builderClass.withFields(builderFields);
    builderClass.withMethods(createMethods(psiClass, psiMethod, builderClass, psiBuilderType, psiAnnotation, builderFields));

    return builderClass;
  }

  @NotNull
  public PsiClass createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final PsiType psiBuilderType = getBuilderType(psiClass);
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);

    LombokLightClassBuilder builderClass = createBuilderClass(psiClass, psiClass, builderClassName, psiAnnotation);
    builderClass.withConstructors(createConstructors(builderClass, psiAnnotation));

    final Collection<PsiField> builderFields = createFields(psiClass, builderClass);
    builderClass.withFields(builderFields);
    builderClass.withMethods(createMethods(psiClass, null, builderClass, psiBuilderType, psiAnnotation, builderFields));

    return builderClass;
  }

  @NotNull
  public Collection<PsiMethod> createMethods(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass psiBuilderClass, @NotNull PsiType psiBuilderType, @NotNull PsiAnnotation psiAnnotation, @NotNull Collection<PsiField> fields) {
    final Collection<PsiMethod> methodsIntern = PsiClassUtil.collectClassMethodsIntern(psiBuilderClass);
    final Set<String> existedMethodNames = new HashSet<String>(methodsIntern.size());
    for (PsiMethod existedMethod : methodsIntern) {
      existedMethodNames.add(existedMethod.getName());
    }

    Collection<PsiMethod> psiMethods = new ArrayList<PsiMethod>();
    psiMethods.addAll(createFieldMethods(fields, psiParentClass, psiBuilderClass, psiAnnotation, existedMethodNames));
    final String buildMethodName = getBuildMethodName(psiAnnotation);
    if (!existedMethodNames.contains(buildMethodName)) {
      psiMethods.add(createBuildMethod(psiParentClass, psiMethod, psiBuilderClass, psiBuilderType, buildMethodName));
    }
    if (!existedMethodNames.contains(ToStringProcessor.METHOD_NAME)) {
      psiMethods.add(toStringProcessor.createToStringMethod(psiBuilderClass, Arrays.asList(psiBuilderClass.getFields()), psiAnnotation));
    }
    return psiMethods;
  }

  @NotNull
  protected LombokLightClassBuilder createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiTypeParameterListOwner psiTypeParameterListOwner, @NotNull String builderClassName, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final Project project = psiClass.getProject();
    return new LombokLightClassBuilder(project, builderClassName, builderClassQualifiedName)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withParameterTypes(psiTypeParameterListOwner.getTypeParameterList())
        .withModifier(PsiModifier.PUBLIC)
        .withModifier(PsiModifier.STATIC);
  }

  @NotNull
  public Collection<PsiMethod> createConstructors(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final Collection<PsiMethod> methodsIntern = PsiClassUtil.collectClassConstructorIntern(psiClass);

    final String constructorName = noArgsConstructorProcessor.getConstructorName(psiClass);
    for (PsiMethod existedConstructor : methodsIntern) {
      if (constructorName.equals(existedConstructor.getName()) && existedConstructor.getParameterList().getParametersCount() == 0) {
        return Collections.emptySet();
      }
    }
    return noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation);
  }

  @NotNull
  private Collection<PsiField> getBuilderFields(@NotNull PsiClass psiClass, @NotNull Collection<PsiField> existedFields) {
    final List<PsiField> fields = new ArrayList<PsiField>();

    final Set<String> existedFieldNames = new HashSet<String>(existedFields.size());
    for (PsiField existedField : existedFields) {
      existedFieldNames.add(existedField.getName());
    }

    for (PsiField psiField : psiClass.getFields()) {
      boolean selectField = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        selectField = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields that start with $
        selectField &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        // skip initialized final fields
        selectField &= !(null != psiField.getInitializer() && modifierList.hasModifierProperty(PsiModifier.FINAL));
        // skip fields already defined in builder class
        selectField &= !existedFieldNames.contains(psiField.getName());
      }
      if (selectField) {
        fields.add(psiField);
      }
    }
    return fields;
  }

  @NotNull
  public Collection<PsiField> createFields(@NotNull PsiClass psiClass, @NotNull PsiClass psiBuilderClass) {
    return createFields(psiClass, Collections.<PsiField>emptySet(), psiBuilderClass);
  }

  @NotNull
  public Collection<PsiField> createFields(@NotNull PsiClass psiClass, @NotNull Collection<PsiField> existedFields, @NotNull PsiClass psiBuilderClass) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiClass);

    List<PsiField> fields = new ArrayList<PsiField>();
    for (PsiField psiField : getBuilderFields(psiClass, existedFields)) {
      final PsiAnnotation singularAnnotation = PsiAnnotationUtil.findAnnotation(psiField, Singular.class);
      AbstractSingularHandler handler = SingularHandlerFactory.getHandlerFor(psiField, singularAnnotation);
      handler.addBuilderField(fields, psiField, psiBuilderClass, accessorsInfo);
    }
    return fields;
  }

  @NotNull
  public Collection<PsiField> createFields(@NotNull PsiMethod psiMethod, @NotNull PsiClass psiBuilderClass) {
    return createFields(psiMethod, Collections.<PsiField>emptySet(), psiBuilderClass);
  }

  @NotNull
  public Collection<PsiField> createFields(@NotNull PsiMethod psiMethod, @NotNull Collection<PsiField> existedFields, @NotNull PsiClass psiBuilderClass) {
    final Set<String> existedFieldNames = new HashSet<String>(existedFields.size());
    for (PsiField existedField : existedFields) {
      existedFieldNames.add(existedField.getName());
    }

    List<PsiField> fields = new ArrayList<PsiField>();
    for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
      final String parameterName = psiParameter.getName();
      if (null != parameterName && !existedFieldNames.contains(parameterName)) {
        final PsiAnnotation singularAnnotation = PsiAnnotationUtil.findAnnotation(psiParameter, Singular.class);
        AbstractSingularHandler handler = SingularHandlerFactory.getHandlerFor(psiParameter, singularAnnotation);
        handler.addBuilderField(fields, psiParameter, psiBuilderClass, AccessorsInfo.EMPTY);
      }
    }
    return fields;
  }

  @NotNull
  protected Collection<PsiMethod> createFieldMethods(@NotNull Collection<PsiField> psiFields, @NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation, @NotNull Collection<String> existedMethodNames) {
    final boolean fluentBuilder = isFluentBuilder(psiAnnotation);
    final PsiType returnType = createSetterReturnType(psiAnnotation, PsiClassUtil.getTypeWithGenerics(innerClass));

    final AccessorsInfo accessorsInfo = AccessorsInfo.build(parentClass);

    final List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (PsiField psiField : psiFields) {
      boolean createMethod = true;
      PsiModifierList modifierList = psiField.getModifierList();
      final String psiFieldName = psiField.getName();
      if (null != modifierList) {
        //Skip static fields.
        createMethod = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields that start with $
        createMethod &= !psiFieldName.startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        // skip initialized final fields
        createMethod &= !(null != psiField.getInitializer() && modifierList.hasModifierProperty(PsiModifier.FINAL));
        // skip methods already defined in builder class
        createMethod &= !existedMethodNames.contains(psiField.getName());
      }
      if (createMethod) {
        // get Field of the root class not of the Builder class
        final PsiVariable originalFieldElement = (PsiVariable) psiField.getNavigationElement();

        final PsiAnnotation singularAnnotation = PsiAnnotationUtil.findAnnotation(originalFieldElement, Singular.class);
        AbstractSingularHandler handler = SingularHandlerFactory.getHandlerFor(originalFieldElement, singularAnnotation);
        handler.addBuilderMethod(methods, originalFieldElement, innerClass, fluentBuilder, returnType, singularAnnotation, accessorsInfo);
      }
    }
    return methods;
  }

  @NotNull
  private PsiMethod createBuildMethod(@NotNull PsiClass parentClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass builderClass, @NotNull PsiType psiBuilderType, @NotNull String buildMethodName) {
    final String codeBlockFormat;

    final String callExpressionText;
    if (null == psiMethod) {
      codeBlockFormat = "return new %s(%s);";
      callExpressionText = psiBuilderType.getPresentableText();
    } else {
      if (PsiType.VOID.equals(psiBuilderType)) {
        codeBlockFormat = "%s(%s);";
      } else if (psiMethod.isConstructor()) {
        codeBlockFormat = "return new %s(%s);";
      } else {
        codeBlockFormat = "return %s(%s);";
      }
      callExpressionText = psiMethod.getName();
    }

    final String callParameterText = joinParameters(builderClass.getFields());

    final PsiCodeBlock psiCodeBlock = PsiMethodUtil.createCodeBlockFromText(
        String.format(codeBlockFormat, callExpressionText, callParameterText),
        builderClass);

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(parentClass.getManager(), buildMethodName)
        .withMethodReturnType(psiBuilderType)
        .withContainingClass(builderClass)
        .withNavigationElement(parentClass)
        .withModifier(PsiModifier.PUBLIC)
        .withBody(psiCodeBlock);

    if (null == psiMethod) {
      final Collection<PsiMethod> classConstructors = PsiClassUtil.collectClassConstructorIntern(parentClass);
      if (!classConstructors.isEmpty()) {
        final PsiMethod constructor = classConstructors.iterator().next();
        addExceptions(methodBuilder, constructor);
      }
    } else {
      addExceptions(methodBuilder, psiMethod);
    }

    addTypeParameters(builderClass, psiMethod, methodBuilder);

    return methodBuilder;
  }

  private void addExceptions(LombokLightMethodBuilder methodBuilder, PsiMethod psiMethod) {
    for (PsiClassType psiClassType : psiMethod.getThrowsList().getReferencedTypes()) {
      methodBuilder.withException(psiClassType);
    }
  }

  private void addTypeParameters(PsiClass builderClass, PsiMethod psiMethod, LombokLightMethodBuilder methodBuilder) {
    final PsiTypeParameter[] psiTypeParameters;
    if (null == psiMethod) {
      psiTypeParameters = builderClass.getTypeParameters();
    } else {
      psiTypeParameters = psiMethod.getTypeParameters();
    }

    for (PsiTypeParameter psiTypeParameter : psiTypeParameters) {
      methodBuilder.withTypeParameter(psiTypeParameter);
    }
  }

  private String joinParameters(PsiField[] psiFields) {
    final StringBuilder builder = new StringBuilder(psiFields.length * 10);
    for (PsiField psiField : psiFields) {
      builder.append(psiField.getName()).append(',');
    }
    if (psiFields.length > 0) {
      builder.deleteCharAt(builder.length() - 1);
    }
    return builder.toString();
  }

  public static final String ANNOTATION_FLUENT = "fluent";
  public static final String ANNOTATION_CHAIN = "chain";

  private boolean isFluentBuilder(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, ANNOTATION_FLUENT, true);
  }

  @NotNull
  private PsiType createSetterReturnType(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiType fieldType) {
    final boolean isChain = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, ANNOTATION_CHAIN, true);
    return isChain ? fieldType : PsiType.VOID;
  }
}
