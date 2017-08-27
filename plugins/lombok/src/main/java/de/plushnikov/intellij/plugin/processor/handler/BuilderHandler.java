package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.ShouldGenerateFullCodeBlock;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.handler.singular.AbstractSingularHandler;
import de.plushnikov.intellij.plugin.processor.handler.singular.BuilderElementHandler;
import de.plushnikov.intellij.plugin.processor.handler.singular.SingularHandlerFactory;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.ErrorMessages;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import de.plushnikov.intellij.util.StringUtils;
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
  private static final String ANNOTATION_BUILD_METHOD_NAME = "buildMethodName";
  private static final String ANNOTATION_BUILDER_METHOD_NAME = "builderMethodName";

  private final static String BUILDER_CLASS_NAME = "Builder";
  private final static String BUILD_METHOD_NAME = "build";
  private final static String BUILDER_METHOD_NAME = "builder";
  private static final String TO_BUILDER_METHOD_NAME = "toBuilder";
  private static final String TO_BUILDER_ANNOTATION_KEY = "toBuilder";

  @SuppressWarnings("deprecation")
  private static final Collection<String> INVALID_ON_BUILDERS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
    Getter.class.getSimpleName(), Setter.class.getSimpleName(), Wither.class.getSimpleName(), ToString.class.getSimpleName(), EqualsAndHashCode.class.getSimpleName(),
    RequiredArgsConstructor.class.getSimpleName(), AllArgsConstructor.class.getSimpleName(), NoArgsConstructor.class.getSimpleName(),
    Data.class.getSimpleName(), Value.class.getSimpleName(), lombok.experimental.Value.class.getSimpleName(), FieldDefaults.class.getSimpleName())));

  private static final String BUILDER_OBTAIN_VIA_FIELD = "field";
  private static final String BUILDER_OBTAIN_VIA_METHOD = "method";
  private static final String BUILDER_OBTAIN_VIA_STATIC = "isStatic";
  private static final String BUILDER_OBTAIN_VIA_ANNOTATION = Builder.ObtainVia.class.getName().replace("$", ".");


  private final ToStringProcessor toStringProcessor;
  private final NoArgsConstructorProcessor noArgsConstructorProcessor;

  public BuilderHandler(ToStringProcessor toStringProcessor, NoArgsConstructorProcessor noArgsConstructorProcessor) {
    this.toStringProcessor = toStringProcessor;
    this.noArgsConstructorProcessor = noArgsConstructorProcessor;
  }

  public static PsiSubstitutor getBuilderSubstitutor(@NotNull PsiTypeParameterListOwner classOrMethodToBuild, @NotNull PsiClass innerClass) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (innerClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiTypeParameter[] typeParameters = classOrMethodToBuild.getTypeParameters();
      PsiTypeParameter[] builderParams = innerClass.getTypeParameters();
      if (typeParameters.length == builderParams.length) {
        for (int i = 0; i < typeParameters.length; i++) {
          PsiTypeParameter typeParameter = typeParameters[i];
          substitutor = substitutor.put(typeParameter, PsiSubstitutor.EMPTY.substitute(builderParams[i]));
        }
      }
    }
    return substitutor;
  }


  public boolean validate(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder problemBuilder) {
    boolean result = validateAnnotationOnRightType(psiClass, problemBuilder);
    if (result) {
      final String builderClassName = getBuilderClassName(psiClass, psiAnnotation);
      result = validateBuilderClassName(builderClassName, psiAnnotation.getProject(), problemBuilder) &&
        validateExistingBuilderClass(builderClassName, psiClass, problemBuilder) &&
        validateSingular(psiClass, problemBuilder) &&
        validateObtainViaAnnotations(getBuilderFields(psiClass, Collections.<PsiField>emptySet(), AccessorsInfo.EMPTY), problemBuilder);
    }
    return result;
  }

  private boolean validateSingular(@NotNull PsiClass psiClass, @NotNull ProblemBuilder problemBuilder) {
    boolean result = true;

    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiClass);
    final Collection<PsiField> builderFields = getBuilderFields(psiClass, Collections.<PsiField>emptySet(), accessorsInfo);
    for (PsiVariable builderVariable : builderFields) {
      final PsiAnnotation singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(builderVariable, Singular.class);
      if (null != singularAnnotation) {
        final String qualifiedName = PsiTypeUtil.getQualifiedName(builderVariable.getType());
        if (SingularHandlerFactory.isInvalidSingularType(qualifiedName)) {
          problemBuilder.addError("Lombok does not know how to create the singular-form builder methods for type '%s'; " +
            "they won't be generated.", qualifiedName != null ? qualifiedName : builderVariable.getType().getCanonicalText());
          result = false;
        }

        final String variableName = builderVariable.getName();
        if (!AbstractSingularHandler.validateSingularName(singularAnnotation, accessorsInfo.removePrefix(variableName))) {
          problemBuilder.addError("Can't singularize this name: \"%s\"; please specify the singular explicitly (i.e. @Singular(\"sheep\"))", variableName);
          result = false;
        }
      }
    }
    return result;
  }

  private boolean validateBuilderClassName(@NotNull String builderClassName, @NotNull Project project, @NotNull ProblemBuilder builder) {
    final PsiNameHelper psiNameHelper = PsiNameHelper.getInstance(project);
    if (!psiNameHelper.isIdentifier(builderClassName)) {
      builder.addError("%s is not a valid identifier", builderClassName);
      return false;
    }
    return true;
  }

  private boolean validateExistingBuilderClass(@NotNull String builderClassName, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    for (PsiClass psiInnerClass : PsiClassUtil.collectInnerClassesIntern(psiClass)) {
      if (builderClassName.equals(psiInnerClass.getName())) {
        if (PsiAnnotationSearchUtil.checkAnnotationsSimpleNameExistsIn(psiInnerClass, INVALID_ON_BUILDERS)) {
          builder.addError("Lombok annotations are not allowed on builder class.");
          return false;
        }
      }
    }
    return true;
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
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
      final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiMethod);
      result = validateBuilderClassName(builderClassName, psiAnnotation.getProject(), problemBuilder) &&
        validateExistingBuilderClass(builderClassName, psiClass, problemBuilder) &&
        validateObtainViaAnnotations(getBuilderParameters(psiMethod, Collections.<PsiField>emptySet()), problemBuilder);
    }
    return result;
  }

  private boolean validateObtainViaAnnotations(@NotNull Collection<? extends PsiVariable> psiVaraibles, @NotNull ProblemBuilder problemBuilder) {
    boolean result = true;
    for (PsiVariable psiVariable : psiVaraibles) {
      final PsiAnnotation obtainViaAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiVariable, BUILDER_OBTAIN_VIA_ANNOTATION);
      if (null != obtainViaAnnotation) {
        final String viaFieldName = PsiAnnotationUtil.getStringAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_FIELD);
        final String viaMethodName = PsiAnnotationUtil.getStringAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_METHOD);
        final boolean viaStaticCall = PsiAnnotationUtil.getBooleanAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_STATIC, false);

        if (StringUtils.isEmpty(viaFieldName) == StringUtils.isEmpty(viaMethodName)) {
          problemBuilder.addError("The syntax is either @ObtainVia(field = \"fieldName\") or @ObtainVia(method = \"methodName\").");
          result = false;
        }
        if (StringUtils.isEmpty(viaMethodName) && viaStaticCall) {
          problemBuilder.addError("@ObtainVia(isStatic = true) is not valid unless 'method' has been set.");
          result = false;
        }
      }
    }
    return result;
  }

  public boolean notExistInnerClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    return notExistInnerClass(psiClass, null, psiAnnotation);
  }

  public boolean notExistInnerClass(@NotNull PsiClass psiClass, @Nullable PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiMethod);
    final PsiClass innerBuilderClass = PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);
    return null == innerBuilderClass;
  }

  private PsiType getReturnTypeOfBuildMethod(@NotNull PsiClass psiClass, @Nullable PsiMethod psiMethod) {
    final PsiType result;
    if (null == psiMethod || psiMethod.isConstructor()) {
      result = PsiClassUtil.getTypeWithGenerics(psiClass);
    } else {
      result = psiMethod.getReturnType();
    }
    return result;
  }

  @NotNull
  private static String getBuildMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String buildMethodName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_BUILD_METHOD_NAME);
    return StringUtil.isEmptyOrSpaces(buildMethodName) ? BUILD_METHOD_NAME : buildMethodName;
  }

  @NotNull
  private static String getBuilderMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String builderMethodName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_METHOD_NAME);
    return StringUtil.isEmptyOrSpaces(builderMethodName) ? BUILDER_METHOD_NAME : builderMethodName;
  }

  @NotNull
  public String getBuilderClassName(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    return getBuilderClassName(psiClass, psiAnnotation, null);
  }

  @NotNull
  public String getBuilderClassName(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @Nullable PsiMethod psiMethod) {
    String builderClassName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_CLASS_NAME);
    if (StringUtil.isEmptyOrSpaces(builderClassName)) {
      final PsiClass psiBuilderClass;
      if (null != psiMethod && !psiMethod.isConstructor()) {
        final PsiType psiMethodReturnType = psiMethod.getReturnType();
        if (PsiType.VOID.equals(psiMethodReturnType)) {
          return StringUtil.capitalize(PsiType.VOID.getCanonicalText()) + BUILDER_CLASS_NAME;
        } else {
          final PsiClass psiMethodReturnClass = PsiTypesUtil.getPsiClass(psiMethodReturnType);
          psiBuilderClass = null == psiMethodReturnClass ? psiClass : psiMethodReturnClass;
        }
      } else {
        psiBuilderClass = psiClass;
      }
      return StringUtil.capitalize(psiBuilderClass.getName()) + BUILDER_CLASS_NAME;
    }
    return builderClassName;
  }

  private boolean hasMethod(@NotNull PsiClass psiClass, String builderMethodName) {
    final Collection<PsiMethod> existingMethods = PsiClassUtil.collectClassStaticMethodsIntern(psiClass);
    for (PsiMethod existingMethod : existingMethods) {
      if (existingMethod.getName().equals(builderMethodName)) {
        return true;
      }
    }
    return false;
  }

  public void createBuilderMethodIfNecessary(@NotNull Collection<? super PsiElement> target, @NotNull PsiClass containingClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass builderPsiClass, @NotNull PsiAnnotation psiAnnotation) {
    final String builderMethodName = getBuilderMethodName(psiAnnotation);
    if (!hasMethod(containingClass, builderMethodName)) {
      final PsiType psiTypeWithGenerics = PsiClassUtil.getTypeWithGenerics(builderPsiClass);

      final LombokLightMethodBuilder method = new LombokLightMethodBuilder(containingClass.getManager(), builderMethodName)
        .withMethodReturnType(psiTypeWithGenerics)
        .withContainingClass(containingClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(PsiModifier.PUBLIC)
        .withBody(createBuilderMethodCodeBlock(containingClass, psiTypeWithGenerics));

      addTypeParameters(builderPsiClass, psiMethod, method);

      if (null == psiMethod || psiMethod.isConstructor() || psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        method.withModifier(PsiModifier.STATIC);
      }
      target.add(method);
    }
  }

  public void createToBuilderMethodIfNecessary(@NotNull Collection<? super PsiElement> target, @NotNull PsiClass containingClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass builderPsiClass, @NotNull PsiAnnotation psiAnnotation) {
    if (PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, TO_BUILDER_ANNOTATION_KEY, false)) {

      final PsiType psiTypeWithGenerics;
      if (null != psiMethod) {
        psiTypeWithGenerics = calculateResultType(psiMethod, builderPsiClass, containingClass);
      } else {
        psiTypeWithGenerics = PsiClassUtil.getTypeWithGenerics(builderPsiClass);
      }

      final LombokLightMethodBuilder method = new LombokLightMethodBuilder(containingClass.getManager(), TO_BUILDER_METHOD_NAME)
        .withMethodReturnType(psiTypeWithGenerics)
        .withContainingClass(containingClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(PsiModifier.PUBLIC);

      method.withBody(createToBuilderMethodCodeBlock(containingClass, psiMethod, psiTypeWithGenerics));
      target.add(method);
    }
  }

  private PsiType calculateResultType(@NotNull PsiMethod psiMethod, PsiClass builderPsiClass, PsiClass psiClass) {
    final Collection<PsiParameter> builderParameters = getBuilderParameters(psiMethod, Collections.<PsiField>emptySet());

    final Collection<PsiType> types = new ArrayList<>();
    for (PsiVariable psiVariable : builderParameters) {

      final PsiAnnotation obtainViaAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiVariable, BUILDER_OBTAIN_VIA_ANNOTATION);
      if (null != obtainViaAnnotation) {
        final String viaFieldName = PsiAnnotationUtil.getStringAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_FIELD);

        final PsiField fieldByName = psiClass.findFieldByName(viaFieldName, false);
        if (fieldByName != null) {
          psiVariable = fieldByName;
        }
      }


      final PsiType psiVariableType = psiVariable.getType();

      if (psiVariableType instanceof PsiClassReferenceType) {
        final PsiClass resolvedPsiVariableClass = ((PsiClassReferenceType) psiVariableType).resolve();
        if (resolvedPsiVariableClass instanceof PsiTypeParameter) {
          types.add(psiVariableType);
        }
      }
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    return factory.createType(builderPsiClass, types.toArray(new PsiType[types.size()]));
  }

  @NotNull
  private PsiCodeBlock createBuilderMethodCodeBlock(@NotNull PsiClass containingClass, @NotNull PsiType psiTypeWithGenerics) {
    final String blockText;
    if (isShouldGenerateFullBodyBlock()) {
      blockText = String.format("return new %s();", psiTypeWithGenerics.getPresentableText());
    } else {
      blockText = "return null;";
    }
    return PsiMethodUtil.createCodeBlockFromText(blockText, containingClass);
  }

  @NotNull
  private PsiCodeBlock createToBuilderMethodCodeBlock(@NotNull PsiClass psiClass, @Nullable PsiMethod psiMethod, @NotNull PsiType psiTypeWithGenerics) {
    final String codeBlockValue;
    if (isShouldGenerateFullBodyBlock()) {
      final AccessorsInfo accessorsInfo;
      final Collection<? extends PsiVariable> builderVariables;

      if (null == psiMethod) {
        accessorsInfo = AccessorsInfo.build(psiClass);
        builderVariables = getBuilderFields(psiClass, Collections.<PsiField>emptySet(), accessorsInfo);
      } else {
        accessorsInfo = AccessorsInfo.EMPTY;
        builderVariables = getBuilderParameters(psiMethod, Collections.<PsiField>emptySet());
      }

      final StringBuilder methodCalls = new StringBuilder();
      for (PsiVariable psiVariable : builderVariables) {
        methodCalls.append('.');
        methodCalls.append(accessorsInfo.removePrefix(psiVariable.getName()));
        methodCalls.append('(');

        final PsiAnnotation obtainViaAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiVariable, BUILDER_OBTAIN_VIA_ANNOTATION);
        if (null != obtainViaAnnotation) {
          final String viaFieldName = PsiAnnotationUtil.getStringAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_FIELD);
          final String viaMethodName = PsiAnnotationUtil.getStringAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_METHOD);

          if (StringUtils.isNotEmpty(viaFieldName)) {
            methodCalls.append("this.");
            methodCalls.append(viaFieldName);
          } else if (StringUtils.isNotEmpty(viaMethodName)) {
            final boolean viaStaticCall = PsiAnnotationUtil.getBooleanAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_STATIC, false);

            methodCalls.append(viaStaticCall ? psiClass.getName() : "this");
            methodCalls.append('.');
            methodCalls.append(viaMethodName);
            methodCalls.append(viaStaticCall ? "(this)" : "()");
          } else {
            methodCalls.append("this.");
            methodCalls.append(psiVariable.getName());
          }
        } else {
          methodCalls.append("this.");
          methodCalls.append(psiVariable.getName());
        }

        methodCalls.append(')');
      }

      codeBlockValue = String.format("new %s()%s", psiTypeWithGenerics.getPresentableText(), methodCalls.toString());
    } else {
      codeBlockValue = "null";
    }
    return PsiMethodUtil.createCodeBlockFromText(String.format("return %s;", codeBlockValue), psiClass);
  }

  private boolean isShouldGenerateFullBodyBlock() {
    return ShouldGenerateFullCodeBlock.getInstance().isStateActive();
  }

  @NotNull
  public PsiClass createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiMethod);

    LombokLightClassBuilder builderClass = createBuilderClass(psiClass, psiMethod, builderClassName,
      psiMethod.isConstructor() || psiMethod.hasModifierProperty(PsiModifier.STATIC), psiAnnotation);
    builderClass.withConstructors(createConstructors(builderClass, psiAnnotation));

    final Collection<PsiParameter> builderParameters = getBuilderParameters(psiMethod, Collections.<PsiField>emptySet());
    final PsiSubstitutor builderSubstitutor = getBuilderSubstitutor(psiClass, builderClass);
    builderClass.withFields(generateFields(builderParameters, builderClass, AccessorsInfo.EMPTY, builderSubstitutor));
    builderClass.withMethods(createMethods(psiClass, psiMethod, builderClass, psiAnnotation, builderParameters, builderSubstitutor));

    return builderClass;
  }

  @NotNull
  public PsiClass createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation);

    LombokLightClassBuilder builderClass = createBuilderClass(psiClass, psiClass, builderClassName, true, psiAnnotation);
    builderClass.withConstructors(createConstructors(builderClass, psiAnnotation));

    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiClass);
    final Collection<PsiField> psiFields = getBuilderFields(psiClass, Collections.<PsiField>emptySet(), accessorsInfo);

    final PsiSubstitutor builderSubstitutor = getBuilderSubstitutor(psiClass, builderClass);
    builderClass.withFields(generateFields(psiFields, builderClass, accessorsInfo, builderSubstitutor));
    builderClass.withMethods(createMethods(psiClass, null, builderClass, psiAnnotation, psiFields, builderSubstitutor));

    return builderClass;
  }

  @NotNull
  public Collection<PsiMethod> createMethods(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass psiBuilderClass,
                                             @NotNull PsiAnnotation psiAnnotation, @NotNull Collection<? extends PsiVariable> psiVariables,
                                             @NotNull PsiSubstitutor builderSubstitutor) {
    final Collection<PsiMethod> methodsIntern = PsiClassUtil.collectClassMethodsIntern(psiBuilderClass);
    final Set<String> existedMethodNames = new HashSet<String>(methodsIntern.size());
    for (PsiMethod existedMethod : methodsIntern) {
      existedMethodNames.add(existedMethod.getName());
    }

    final List<PsiMethod> psiMethods = new ArrayList<PsiMethod>();

    // use AccessorsInfo only for @Builder on class, not on method
    final AccessorsInfo accessorsInfo = null == psiMethod ? AccessorsInfo.build(psiParentClass) : AccessorsInfo.EMPTY;

    final boolean fluentBuilder = isFluentBuilder(psiAnnotation);
    final PsiType psiBuilderClassType = PsiClassUtil.getTypeWithGenerics(psiBuilderClass);
    final PsiType returnType = createSetterReturnType(psiAnnotation, psiBuilderClassType);

    final StringBuilder buildMethodPrepareString = new StringBuilder(psiVariables.size() * 20);
    final StringBuilder buildMethodParameterString = new StringBuilder(psiVariables.size() * 20);
    for (PsiVariable psiVariable : psiVariables) {
      final String fieldName = accessorsInfo.removePrefix(psiVariable.getName());

      final PsiAnnotation singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiVariable, Singular.class);
      final BuilderElementHandler handler = SingularHandlerFactory.getHandlerFor(psiVariable, singularAnnotation, isShouldGenerateFullBodyBlock());

      // skip methods already defined in builder class
      if (!existedMethodNames.contains(fieldName)) {
        final String singularName = handler.createSingularName(singularAnnotation, fieldName);
        handler.addBuilderMethod(psiMethods, psiVariable, fieldName, psiBuilderClass, fluentBuilder, returnType, singularName, builderSubstitutor);
      }

      handler.appendBuildPrepare(buildMethodPrepareString, psiVariable, fieldName);
      handler.appendBuildCall(buildMethodParameterString, fieldName);
      buildMethodParameterString.append(',');
    }

    final String buildMethodName = getBuildMethodName(psiAnnotation);
    if (!existedMethodNames.contains(buildMethodName)) {

      if (buildMethodParameterString.length() > 0) {
        buildMethodParameterString.deleteCharAt(buildMethodParameterString.length() - 1);
      }

      psiMethods.add(createBuildMethod(psiParentClass, psiMethod, psiBuilderClass, builderSubstitutor,
        buildMethodName, buildMethodPrepareString.toString(), buildMethodParameterString.toString()));
    }

    if (!existedMethodNames.contains(ToStringProcessor.METHOD_NAME)) {
      psiMethods.add(toStringProcessor.createToStringMethod(psiBuilderClass, Arrays.asList(psiBuilderClass.getFields()), psiAnnotation));
    }
    return psiMethods;
  }

  @NotNull
  private LombokLightClassBuilder createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiTypeParameterListOwner psiTypeParameterListOwner, @NotNull String builderClassName, final boolean isStatic, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final Project project = psiClass.getProject();
    final LombokLightClassBuilder classBuilder = new LombokLightClassBuilder(project, builderClassName, builderClassQualifiedName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withParameterTypes(psiTypeParameterListOwner instanceof PsiMethod && ((PsiMethod) psiTypeParameterListOwner).isConstructor() ? psiClass.getTypeParameterList() : psiTypeParameterListOwner.getTypeParameterList())
      .withModifier(PsiModifier.PUBLIC);
    if (isStatic) {
      classBuilder.withModifier(PsiModifier.STATIC);
    }
    return classBuilder;
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
  public Collection<PsiField> getBuilderFields(@NotNull PsiClass psiClass, @NotNull Collection<PsiField> existedFields, @NotNull AccessorsInfo accessorsInfo) {
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
        // skip initialized final fields
        selectField &= !(null != psiField.getInitializer() && modifierList.hasModifierProperty(PsiModifier.FINAL));
      }
      //Skip fields that start with $
      final String psiFieldName = psiField.getName();
      if (null != psiFieldName) {
        selectField &= !psiFieldName.startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);

        if (!existedFieldNames.isEmpty()) {
          // skip fields already defined in builder class
          final String fieldName = accessorsInfo.removePrefix(psiFieldName);
          selectField &= !existedFieldNames.contains(fieldName);
        }
      }

      if (selectField) {
        fields.add(psiField);
      }
    }
    return fields;
  }

  @NotNull
  public Collection<PsiField> generateFields(@NotNull Collection<? extends PsiVariable> psiVariables, @NotNull PsiClass psiBuilderClass,
                                             @NotNull AccessorsInfo accessorsInfo, @NotNull PsiSubstitutor builderSubstitutor) {
    List<PsiField> fields = new ArrayList<PsiField>();
    for (PsiVariable psiVariable : psiVariables) {
      final PsiAnnotation singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiVariable, Singular.class);
      BuilderElementHandler handler = SingularHandlerFactory.getHandlerFor(psiVariable, singularAnnotation, isShouldGenerateFullBodyBlock());
      handler.addBuilderField(fields, psiVariable, psiBuilderClass, accessorsInfo, builderSubstitutor);
    }
    return fields;
  }

  @NotNull
  public Collection<PsiParameter> getBuilderParameters(@NotNull PsiMethod psiMethod, @NotNull Collection<PsiField> existedFields) {
    final Set<String> existedFieldNames = new HashSet<String>(existedFields.size());
    for (PsiField existedField : existedFields) {
      existedFieldNames.add(existedField.getName());
    }

    Collection<PsiParameter> result = new ArrayList<PsiParameter>();

    for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
      final String parameterName = psiParameter.getName();
      if (null != parameterName && !existedFieldNames.contains(parameterName)) {
        result.add(psiParameter);
      }
    }
    return result;
  }

  @NotNull
  private PsiMethod createBuildMethod(@NotNull PsiClass parentClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass builderClass, @NotNull PsiSubstitutor builderSubstitutor,
                                      @NotNull String buildMethodName, @NotNull String buildMethodPrepare, @NotNull String buildMethodParameters) {
    final PsiType builderType = getReturnTypeOfBuildMethod(parentClass, psiMethod);
    final PsiType returnType = builderSubstitutor.substitute(builderType);

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(parentClass.getManager(), buildMethodName)
      .withMethodReturnType(returnType)
      .withContainingClass(builderClass)
      .withNavigationElement(parentClass)
      .withModifier(PsiModifier.PUBLIC)
      .withBody(createBuildMethodCodeBlock(psiMethod, builderClass, returnType, buildMethodPrepare, buildMethodParameters));

    if (null == psiMethod) {
      final Collection<PsiMethod> classConstructors = PsiClassUtil.collectClassConstructorIntern(parentClass);
      if (!classConstructors.isEmpty()) {
        final PsiMethod constructor = classConstructors.iterator().next();
        addExceptions(methodBuilder, constructor);
      }
    } else {
      addExceptions(methodBuilder, psiMethod);
    }

    return methodBuilder;
  }

  @NotNull
  private PsiCodeBlock createBuildMethodCodeBlock(@Nullable PsiMethod psiMethod, @NotNull PsiClass psiClass, @NotNull PsiType buildMethodReturnType,
                                                  @NotNull String buildMethodPrepare, @NotNull String buildMethodParameters) {
    final String blockText;

    if (isShouldGenerateFullBodyBlock()) {
      final String codeBlockFormat, callExpressionText;

      if (null == psiMethod || psiMethod.isConstructor()) {
        codeBlockFormat = "%s\n return new %s(%s);";
        callExpressionText = buildMethodReturnType.getPresentableText();
      } else {
        if (PsiType.VOID.equals(buildMethodReturnType)) {
          codeBlockFormat = "%s\n %s(%s);";
        } else {
          codeBlockFormat = "%s\n return %s(%s);";
        }
        callExpressionText = calculateCallExpressionForMethod(psiMethod, psiClass);
      }
      blockText = String.format(codeBlockFormat, buildMethodPrepare, callExpressionText, buildMethodParameters);
    } else {
      blockText = "return " + PsiTypeUtil.getReturnValueOfType(buildMethodReturnType) + ";";
    }
    return PsiMethodUtil.createCodeBlockFromText(blockText, psiClass);
  }

  @NotNull
  private String calculateCallExpressionForMethod(@NotNull PsiMethod psiMethod, @NotNull PsiClass builderClass) {
    final PsiClass containingClass = psiMethod.getContainingClass();

    StringBuilder className = new StringBuilder();
    if (null != containingClass) {
      className.append(containingClass.getName()).append(".");
      if (!psiMethod.isConstructor() && !psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        className.append("this.");
      }
      if (builderClass.hasTypeParameters()) {
        className.append('<');

        for (PsiTypeParameter typeParameter : builderClass.getTypeParameters()) {
          className.append(typeParameter.getName()).append(',');
        }

        className.setCharAt(className.length() - 1, '>');
      }
    }
    return className + psiMethod.getName();
  }

  private void addExceptions(LombokLightMethodBuilder methodBuilder, PsiMethod psiMethod) {
    for (PsiClassType psiClassType : psiMethod.getThrowsList().getReferencedTypes()) {
      methodBuilder.withException(psiClassType);
    }
  }

  private void addTypeParameters(PsiClass builderClass, PsiMethod psiMethod, LombokLightMethodBuilder methodBuilder) {
    final PsiTypeParameter[] psiTypeParameters;
    if (null == psiMethod || psiMethod.isConstructor()) {
      psiTypeParameters = builderClass.getTypeParameters();
    } else {
      psiTypeParameters = psiMethod.getTypeParameters();
    }

    for (PsiTypeParameter psiTypeParameter : psiTypeParameters) {
      methodBuilder.withTypeParameter(psiTypeParameter);
    }
  }

  // These exist just to support the 'old' lombok.experimental.Builder, which had these properties. lombok.Builder no longer has them.
  private static final String ANNOTATION_FLUENT = "fluent";
  private static final String ANNOTATION_CHAIN = "chain";

  private boolean isFluentBuilder(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, ANNOTATION_FLUENT, true);
  }

  @NotNull
  private PsiType createSetterReturnType(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiType fieldType) {
    final boolean isChain = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, ANNOTATION_CHAIN, true);
    return isChain ? fieldType : PsiType.VOID;
  }
}
