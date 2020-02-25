package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.singular.AbstractSingularHandler;
import de.plushnikov.intellij.plugin.processor.handler.singular.SingularHandlerFactory;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Wither;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.openapi.util.text.StringUtil.replace;
import static de.plushnikov.intellij.plugin.lombokconfig.ConfigKey.BUILDER_CLASS_NAME;

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
  private static final String ANNOTATION_SETTER_PREFIX = "setterPrefix";

  private final static String BUILD_METHOD_NAME = "build";
  private final static String BUILDER_METHOD_NAME = "builder";
  private static final String TO_BUILDER_METHOD_NAME = "toBuilder";
  static final String TO_BUILDER_ANNOTATION_KEY = "toBuilder";

  private static final Collection<String> INVALID_ON_BUILDERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    Getter.class.getSimpleName(), Setter.class.getSimpleName(), Wither.class.getSimpleName(), With.class.getSimpleName(),
    ToString.class.getSimpleName(), EqualsAndHashCode.class.getSimpleName(),
    RequiredArgsConstructor.class.getSimpleName(), AllArgsConstructor.class.getSimpleName(), NoArgsConstructor.class.getSimpleName(),
    Data.class.getSimpleName(), Value.class.getSimpleName(), FieldDefaults.class.getSimpleName())));

  private final ToStringProcessor toStringProcessor;
  private final NoArgsConstructorProcessor noArgsConstructorProcessor;

  public BuilderHandler(@NotNull ToStringProcessor toStringProcessor, @NotNull NoArgsConstructorProcessor noArgsConstructorProcessor) {
    this.toStringProcessor = toStringProcessor;
    this.noArgsConstructorProcessor = noArgsConstructorProcessor;
  }

  PsiSubstitutor getBuilderSubstitutor(@NotNull PsiTypeParameterListOwner classOrMethodToBuild, @NotNull PsiClass innerClass) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (innerClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiTypeParameter[] typeParameters = classOrMethodToBuild.getTypeParameters();
      PsiTypeParameter[] builderParams = innerClass.getTypeParameters();
      if (typeParameters.length <= builderParams.length) {
        for (int i = 0; i < typeParameters.length; i++) {
          PsiTypeParameter typeParameter = typeParameters[i];
          substitutor = substitutor.put(typeParameter, PsiSubstitutor.EMPTY.substitute(builderParams[i]));
        }
      }
    }
    return substitutor;
  }

  public boolean validate(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder problemBuilder) {
    boolean result = validateAnnotationOnRightType(psiClass, psiAnnotation, problemBuilder);
    if (result) {
      final Project project = psiAnnotation.getProject();
      final String builderClassName = getBuilderClassName(psiClass, psiAnnotation);
      final String buildMethodName = getBuildMethodName(psiAnnotation);
      final String builderMethodName = getBuilderMethodName(psiAnnotation);
      result = validateBuilderIdentifier(builderClassName, project, problemBuilder) &&
        validateBuilderIdentifier(buildMethodName, project, problemBuilder) &&
        (builderMethodName.isEmpty() || validateBuilderIdentifier(builderMethodName, project, problemBuilder)) &&
        validateExistingBuilderClass(builderClassName, psiClass, problemBuilder);
      if (result) {
        final Collection<BuilderInfo> builderInfos = createBuilderInfos(psiClass, null).collect(Collectors.toList());
        result = validateSingular(builderInfos, problemBuilder) &&
          validateBuilderDefault(builderInfos, problemBuilder) &&
          validateObtainViaAnnotations(builderInfos.stream(), problemBuilder);
      }
    }
    return result;
  }

  private boolean validateBuilderDefault(@NotNull Collection<BuilderInfo> builderInfos, @NotNull ProblemBuilder problemBuilder) {
    final Optional<BuilderInfo> anyBuilderDefaultAndSingulars = builderInfos.stream()
      .filter(BuilderInfo::hasBuilderDefaultAnnotation)
      .filter(BuilderInfo::hasSingularAnnotation).findAny();
    anyBuilderDefaultAndSingulars.ifPresent(builderInfo -> {
        problemBuilder.addError("@Builder.Default and @Singular cannot be mixed.");
      }
    );

    return !anyBuilderDefaultAndSingulars.isPresent();
  }

  public boolean validate(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder problemBuilder) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    boolean result = null != psiClass;
    if (result) {
      final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiMethod);

      final Project project = psiAnnotation.getProject();
      final String buildMethodName = getBuildMethodName(psiAnnotation);
      final String builderMethodName = getBuilderMethodName(psiAnnotation);
      result = validateBuilderIdentifier(builderClassName, project, problemBuilder) &&
        validateBuilderIdentifier(buildMethodName, project, problemBuilder) &&
        (builderMethodName.isEmpty() || validateBuilderIdentifier(builderMethodName, project, problemBuilder)) &&
        validateExistingBuilderClass(builderClassName, psiClass, problemBuilder);
      if (result) {
        final Stream<BuilderInfo> builderInfos = createBuilderInfos(psiClass, psiMethod);
        result = validateObtainViaAnnotations(builderInfos, problemBuilder);
      }
    }
    return result;
  }

  private boolean validateSingular(Collection<BuilderInfo> builderInfos, @NotNull ProblemBuilder problemBuilder) {
    AtomicBoolean result = new AtomicBoolean(true);

    builderInfos.stream().filter(BuilderInfo::hasSingularAnnotation).forEach(builderInfo -> {
      final PsiType psiVariableType = builderInfo.getVariable().getType();
      final String qualifiedName = PsiTypeUtil.getQualifiedName(psiVariableType);
      if (SingularHandlerFactory.isInvalidSingularType(qualifiedName)) {
        problemBuilder.addError("Lombok does not know how to create the singular-form builder methods for type '%s'; " +
          "they won't be generated.", qualifiedName != null ? qualifiedName : psiVariableType.getCanonicalText());
        result.set(false);
      }

      if (!AbstractSingularHandler.validateSingularName(builderInfo.getSingularAnnotation(), builderInfo.getFieldName())) {
        problemBuilder.addError("Can't singularize this name: \"%s\"; please specify the singular explicitly (i.e. @Singular(\"sheep\"))", builderInfo.getFieldName());
        result.set(false);
      }
    });
    return result.get();
  }

  private boolean validateBuilderIdentifier(@NotNull String builderClassName, @NotNull Project project, @NotNull ProblemBuilder builder) {
    final PsiNameHelper psiNameHelper = PsiNameHelper.getInstance(project);
    if (!psiNameHelper.isIdentifier(builderClassName)) {
      builder.addError("%s is not a valid identifier", builderClassName);
      return false;
    }
    return true;
  }

  public boolean validateExistingBuilderClass(@NotNull String builderClassName, @NotNull PsiClass psiClass, @NotNull ProblemBuilder problemBuilder) {
    final Optional<PsiClass> optionalPsiClass = PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);

    return optionalPsiClass.map(builderClass -> validateInvalidAnnotationsOnBuilderClass(builderClass, problemBuilder)).orElse(true);
  }

  boolean validateInvalidAnnotationsOnBuilderClass(@NotNull PsiClass builderClass, @NotNull ProblemBuilder problemBuilder) {
    if (PsiAnnotationSearchUtil.checkAnnotationsSimpleNameExistsIn(builderClass, INVALID_ON_BUILDERS)) {
      problemBuilder.addError("Lombok annotations are not allowed on builder class.");
      return false;
    }
    return true;
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(String.format("@%s can be used on classes only", psiAnnotation.getQualifiedName()));
      return false;
    }
    return true;
  }

  private boolean validateObtainViaAnnotations(Stream<BuilderInfo> builderInfos, @NotNull ProblemBuilder problemBuilder) {
    AtomicBoolean result = new AtomicBoolean(true);
    builderInfos.map(BuilderInfo::withObtainVia).filter(BuilderInfo::hasObtainViaAnnotation).forEach(builderInfo ->
    {
      if (StringUtil.isEmpty(builderInfo.getViaFieldName()) == StringUtil.isEmpty(builderInfo.getViaMethodName())) {
        problemBuilder.addError("The syntax is either @ObtainVia(field = \"fieldName\") or @ObtainVia(method = \"methodName\").");
        result.set(false);
      }

      if (StringUtil.isEmpty(builderInfo.getViaMethodName()) && builderInfo.isViaStaticCall()) {
        problemBuilder.addError("@ObtainVia(isStatic = true) is not valid unless 'method' has been set.");
        result.set(false);
      }
    });
    return result.get();
  }

  public Optional<PsiClass> getExistInnerBuilderClass(@NotNull PsiClass psiClass, @Nullable PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiMethod);
    return PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);
  }

  PsiType getReturnTypeOfBuildMethod(@NotNull PsiClass psiClass, @Nullable PsiMethod psiMethod) {
    final PsiType result;
    if (null == psiMethod || psiMethod.isConstructor()) {
      result = PsiClassUtil.getTypeWithGenerics(psiClass);
    } else {
      result = psiMethod.getReturnType();
    }
    return result;
  }

  @NotNull
  public String getBuildMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String buildMethodName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_BUILD_METHOD_NAME);
    return StringUtil.isEmptyOrSpaces(buildMethodName) ? BUILD_METHOD_NAME : buildMethodName;
  }

  @NotNull
  String getBuilderMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String builderMethodName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_METHOD_NAME);
    return null == builderMethodName ? BUILDER_METHOD_NAME : builderMethodName;
  }

  @NotNull
  private String getSetterPrefix(@NotNull PsiAnnotation psiAnnotation) {
    final String setterPrefix = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_SETTER_PREFIX);
    return null == setterPrefix ? "" : setterPrefix;
  }

  @NotNull
  @PsiModifier.ModifierConstant
  private String getBuilderOuterAccessVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String accessVisibility = LombokProcessorUtil.getAccessVisibility(psiAnnotation);
    return null == accessVisibility ? PsiModifier.PUBLIC : accessVisibility;
  }

  @NotNull
  @PsiModifier.ModifierConstant
  private String getBuilderInnerAccessVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String accessVisibility = getBuilderOuterAccessVisibility(psiAnnotation);
    return PsiModifier.PROTECTED.equals(accessVisibility) ? PsiModifier.PUBLIC : accessVisibility;
  }

  @NotNull
  private String getBuilderClassName(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    return getBuilderClassName(psiClass, psiAnnotation, null);
  }

  @NotNull
  public String getBuilderClassName(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @Nullable PsiMethod psiMethod) {
    final String builderClassName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_CLASS_NAME);
    if (!StringUtil.isEmptyOrSpaces(builderClassName)) {
      return builderClassName;
    }

    String relevantReturnType = psiClass.getName();

    if (null != psiMethod && !psiMethod.isConstructor()) {
      final PsiType psiMethodReturnType = psiMethod.getReturnType();
      if (null != psiMethodReturnType) {
        relevantReturnType = PsiNameHelper.getQualifiedClassName(psiMethodReturnType.getPresentableText(), false);
      }
    }

    return getBuilderClassName(psiClass, relevantReturnType);
  }

  @NotNull
  String getBuilderClassName(@NotNull PsiClass psiClass, String returnTypeName) {
    final ConfigDiscovery configDiscovery = ConfigDiscovery.getInstance();
    final String builderClassNamePattern = configDiscovery.getStringLombokConfigProperty(BUILDER_CLASS_NAME, psiClass);
    return replace(builderClassNamePattern, "*", capitalize(returnTypeName));
  }

  boolean hasMethod(@NotNull PsiClass psiClass, @NotNull String builderMethodName) {
    final Collection<PsiMethod> existingMethods = PsiClassUtil.collectClassStaticMethodsIntern(psiClass);
    return existingMethods.stream().map(PsiMethod::getName).anyMatch(builderMethodName::equals);
  }

  public Optional<PsiMethod> createBuilderMethodIfNecessary(@NotNull PsiClass containingClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass builderPsiClass, @NotNull PsiAnnotation psiAnnotation) {
    final String builderMethodName = getBuilderMethodName(psiAnnotation);
    if (!builderMethodName.isEmpty() && !hasMethod(containingClass, builderMethodName)) {
      final PsiType psiTypeWithGenerics = PsiClassUtil.getTypeWithGenerics(builderPsiClass);

      final String blockText = String.format("return new %s();", psiTypeWithGenerics.getPresentableText());
      final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(containingClass.getManager(), builderMethodName)
        .withMethodReturnType(psiTypeWithGenerics)
        .withContainingClass(containingClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(getBuilderOuterAccessVisibility(psiAnnotation));
      methodBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(blockText, methodBuilder));

      addTypeParameters(builderPsiClass, psiMethod, methodBuilder);

      if (null == psiMethod || psiMethod.isConstructor() || psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        methodBuilder.withModifier(PsiModifier.STATIC);
      }
      return Optional.of(methodBuilder);
    }
    return Optional.empty();
  }

  public Optional<PsiMethod> createToBuilderMethodIfNecessary(@NotNull PsiClass containingClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass builderPsiClass, @NotNull PsiAnnotation psiAnnotation) {
    if (!PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, TO_BUILDER_ANNOTATION_KEY, false)) {
      return Optional.empty();
    }

    final List<BuilderInfo> builderInfos = createBuilderInfos(psiAnnotation, containingClass, psiMethod, builderPsiClass);
    builderInfos.forEach(BuilderInfo::withObtainVia);

    final PsiType psiTypeWithGenerics;
    if (null != psiMethod) {
      psiTypeWithGenerics = calculateResultType(builderInfos, builderPsiClass, containingClass);
    } else {
      psiTypeWithGenerics = PsiClassUtil.getTypeWithGenerics(builderPsiClass);
    }

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(containingClass.getManager(), TO_BUILDER_METHOD_NAME)
      .withMethodReturnType(psiTypeWithGenerics)
      .withContainingClass(containingClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(getBuilderOuterAccessVisibility(psiAnnotation));

    final String toBuilderMethodCalls = builderInfos.stream()
      .map(BuilderInfo::renderToBuilderCall)
      .collect(Collectors.joining(".", ".", ""));

    final String blockText = String.format("return new %s()%s;", psiTypeWithGenerics.getPresentableText(), toBuilderMethodCalls);
    methodBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(blockText, methodBuilder));

    return Optional.of(methodBuilder);
  }

  private PsiType calculateResultType(@NotNull List<BuilderInfo> builderInfos, PsiClass builderPsiClass, PsiClass psiClass) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    final PsiType[] psiTypes = builderInfos.stream()
      .map(BuilderInfo::getObtainViaFieldVariableType)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .toArray(PsiType[]::new);
    return factory.createType(builderPsiClass, psiTypes);
  }

  @NotNull
  private Stream<BuilderInfo> createBuilderInfos(@NotNull PsiClass psiClass, @Nullable PsiMethod psiClassMethod) {
    final Stream<BuilderInfo> result;
    if (null != psiClassMethod) {
      result = Arrays.stream(psiClassMethod.getParameterList().getParameters()).map(BuilderInfo::fromPsiParameter);
    } else {
      result = PsiClassUtil.collectClassFieldsIntern(psiClass).stream().map(BuilderInfo::fromPsiField)
        .filter(BuilderInfo::useForBuilder);
    }
    return result;
  }

  public List<BuilderInfo> createBuilderInfos(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass,
                                              @Nullable PsiMethod psiClassMethod, @NotNull PsiClass builderClass) {
    final PsiSubstitutor builderSubstitutor = getBuilderSubstitutor(psiClass, builderClass);
    final String accessVisibility = getBuilderInnerAccessVisibility(psiAnnotation);
    final String setterPrefix = getSetterPrefix(psiAnnotation);

    return createBuilderInfos(psiClass, psiClassMethod)
      .map(info -> info.withSubstitutor(builderSubstitutor))
      .map(info -> info.withBuilderClass(builderClass))
      .map(info -> info.withVisibilityModifier(accessVisibility))
      .map(info -> info.withSetterPrefix(setterPrefix))
      .collect(Collectors.toList());
  }

  @NotNull
  public PsiClass createBuilderClass(@NotNull PsiClass psiClass, @Nullable PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    LombokLightClassBuilder builderClass;
    if (null != psiMethod) {
      builderClass = createEmptyBuilderClass(psiClass, psiMethod, psiAnnotation);
    } else {
      builderClass = createEmptyBuilderClass(psiClass, psiAnnotation);
    }
    builderClass.withMethods(createConstructors(builderClass, psiAnnotation));

    final List<BuilderInfo> builderInfos = createBuilderInfos(psiAnnotation, psiClass, psiMethod, builderClass);

    // create builder Fields
    builderInfos.stream()
      .map(BuilderInfo::renderBuilderFields)
      .forEach(builderClass::withFields);

    // create builder methods
    builderInfos.stream()
      .map(BuilderInfo::renderBuilderMethods)
      .forEach(builderClass::withMethods);

    // create 'build' method
    final String buildMethodName = getBuildMethodName(psiAnnotation);
    builderClass.addMethod(createBuildMethod(psiAnnotation, psiClass, psiMethod, builderClass, buildMethodName, builderInfos));

    // create 'toString' method
    builderClass.addMethod(createToStringMethod(psiAnnotation, builderClass));

    return builderClass;
  }

  @NotNull
  private LombokLightClassBuilder createEmptyBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    return createBuilderClass(psiClass, psiMethod,
      psiMethod.isConstructor() || psiMethod.hasModifierProperty(PsiModifier.STATIC), psiAnnotation);
  }

  @NotNull
  private LombokLightClassBuilder createEmptyBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    return createBuilderClass(psiClass, psiClass, true, psiAnnotation);
  }

  public Optional<PsiClass> createBuilderClassIfNotExist(@NotNull PsiClass psiClass, @Nullable PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    PsiClass builderClass = null;
    if (!getExistInnerBuilderClass(psiClass, psiMethod, psiAnnotation).isPresent()) {
      builderClass = createBuilderClass(psiClass, psiMethod, psiAnnotation);
    }
    return Optional.ofNullable(builderClass);
  }

  @NotNull
  public PsiMethod createToStringMethod(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass builderClass) {
    return createToStringMethod(psiAnnotation, builderClass, false);
  }

  @NotNull
  PsiMethod createToStringMethod(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass builderClass, boolean forceCallSuper) {
    final List<EqualsAndHashCodeToStringHandler.MemberInfo> memberInfos = Arrays.stream(builderClass.getFields())
      .map(EqualsAndHashCodeToStringHandler.MemberInfo::new).collect(Collectors.toList());
    return toStringProcessor.createToStringMethod(builderClass, memberInfos, psiAnnotation, forceCallSuper);
  }

  @NotNull
  private LombokLightClassBuilder createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiTypeParameterListOwner psiTypeParameterListOwner, final boolean isStatic, @NotNull PsiAnnotation psiAnnotation) {
    PsiMethod psiMethod = null;
    if (psiTypeParameterListOwner instanceof PsiMethod) {
      psiMethod = (PsiMethod) psiTypeParameterListOwner;
    }

    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiMethod);
    final String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final LombokLightClassBuilder classBuilder = new LombokLightClassBuilder(psiClass, builderClassName, builderClassQualifiedName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withParameterTypes((null != psiMethod && psiMethod.isConstructor()) ? psiClass.getTypeParameterList() : psiTypeParameterListOwner.getTypeParameterList())
      .withModifier(getBuilderOuterAccessVisibility(psiAnnotation));
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
  public PsiMethod createBuildMethod(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass parentClass, @Nullable PsiMethod psiMethod, @NotNull PsiClass builderClass, @NotNull String buildMethodName, List<BuilderInfo> builderInfos) {
    final PsiType builderType = getReturnTypeOfBuildMethod(parentClass, psiMethod);

    final PsiSubstitutor builderSubstitutor = getBuilderSubstitutor(parentClass, builderClass);
    final PsiType returnType = builderSubstitutor.substitute(builderType);

    final String buildMethodPrepare = builderInfos.stream()
      .map(BuilderInfo::renderBuildPrepare)
      .collect(Collectors.joining());

    final String buildMethodParameters = builderInfos.stream()
      .map(BuilderInfo::renderBuildCall)
      .collect(Collectors.joining(","));

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(parentClass.getManager(), buildMethodName)
      .withMethodReturnType(returnType)
      .withContainingClass(builderClass)
      .withNavigationElement(parentClass)
      .withModifier(getBuilderInnerAccessVisibility(psiAnnotation));
    final String codeBlockText = createBuildMethodCodeBlockText(psiMethod, builderClass, returnType, buildMethodPrepare, buildMethodParameters);
    methodBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(codeBlockText, methodBuilder));

    Optional<PsiMethod> definedConstructor = Optional.ofNullable(psiMethod);
    if (!definedConstructor.isPresent()) {
      final Collection<PsiMethod> classConstructors = PsiClassUtil.collectClassConstructorIntern(parentClass);
      definedConstructor = classConstructors.stream()
        .filter(m -> sameParameters(m.getParameterList().getParameters(), builderInfos))
        .findFirst();
    }
    definedConstructor.map(PsiMethod::getThrowsList).map(PsiReferenceList::getReferencedTypes).map(Arrays::stream)
      .ifPresent(stream -> stream.forEach(methodBuilder::withException));

    return methodBuilder;
  }

  private boolean sameParameters(PsiParameter[] parameters, List<BuilderInfo> builderInfos) {
    if (parameters.length != builderInfos.size()) {
      return false;
    }

    final Iterator<BuilderInfo> builderInfoIterator = builderInfos.iterator();
    for (PsiParameter psiParameter : parameters) {
      final BuilderInfo builderInfo = builderInfoIterator.next();
      if (!psiParameter.getType().isAssignableFrom(builderInfo.getFieldType())) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private String createBuildMethodCodeBlockText(@Nullable PsiMethod psiMethod, @NotNull PsiClass psiClass, @NotNull PsiType buildMethodReturnType,
                                                @NotNull String buildMethodPrepare, @NotNull String buildMethodParameters) {
    final String blockText;

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
    return blockText;
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
        className.append(Arrays.stream(builderClass.getTypeParameters()).map(PsiTypeParameter::getName).collect(Collectors.joining(",", "<", ">")));
      }
    }
    return className + psiMethod.getName();
  }

  void addTypeParameters(@NotNull PsiClass builderClass, @Nullable PsiMethod psiMethod, @NotNull LombokLightMethodBuilder methodBuilder) {
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
}
