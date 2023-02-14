package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SuperBuilderHandler extends BuilderHandler {

  private static final String SELF_METHOD = "self";
  private static final String TO_BUILDER_METHOD_NAME = "toBuilder";
  private static final String FILL_VALUES_METHOD_NAME = "$fillValuesFrom";
  private static final String STATIC_FILL_VALUES_METHOD_NAME = "$fillValuesFromInstanceIntoBuilder";
  private static final String INSTANCE_VARIABLE_NAME = "instance";
  private static final String BUILDER_VARIABLE_NAME = "b";

  @Override
  protected boolean validateBuilderConstructor(@NotNull PsiClass psiClass,
                                               Collection<BuilderInfo> builderInfos,
                                               @NotNull ProblemSink problemSink) {
    return true;
  }

  @Override
  public boolean validateExistingBuilderClass(@NotNull String builderClassName,
                                              @NotNull PsiClass psiClass,
                                              @NotNull ProblemSink problemSink) {
    final Optional<PsiClass> existingInnerBuilderClass = PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);

    if (existingInnerBuilderClass.isPresent()) {

      if (!validateInvalidAnnotationsOnBuilderClass(existingInnerBuilderClass.get(), problemSink)) {
        return false;
      }

      final Optional<PsiClass> isStaticAndAbstract = existingInnerBuilderClass
        .filter(psiInnerClass -> psiInnerClass.hasModifierProperty(PsiModifier.STATIC))
        .filter(psiInnerClass -> psiInnerClass.hasModifierProperty(PsiModifier.ABSTRACT));

      if (isStaticAndAbstract.isEmpty()) {
        problemSink.addErrorMessage("inspection.message.existing.builder.must.be.abstract.static.inner.class");
        return false;
      }
    }

    return true;
  }

  @NotNull
  public String getBuilderClassName(@NotNull PsiClass psiClass) {
    return getBuilderClassName(psiClass, psiClass.getName());
  }

  @NotNull
  public String getBuilderImplClassName(@NotNull PsiClass psiClass) {
    return getBuilderClassName(psiClass) + "Impl";
  }

  public Optional<PsiMethod> createBuilderBasedConstructor(@NotNull PsiClass psiClass, @NotNull PsiClass builderClass,
                                                           @NotNull PsiAnnotation psiAnnotation,
                                                           @NotNull PsiClassType psiTypeBaseWithGenerics) {
    final String className = psiClass.getName();
    if (null == className) {
      return Optional.empty();
    }

    final Collection<PsiMethod> existedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
    if (ContainerUtil.exists(existedConstructors, psiMethod -> psiMethod.getParameterList().getParametersCount() == 1)) {
      return Optional.empty();
    }

    LombokLightMethodBuilder constructorBuilderBased = new LombokLightMethodBuilder(psiClass.getManager(), className)
      .withConstructor(true)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PROTECTED)
      .withParameter(BUILDER_VARIABLE_NAME, psiTypeBaseWithGenerics);

    final List<BuilderInfo> builderInfos = createBuilderInfos(psiClass, psiAnnotation, builderClass);
    //dont need initBuilderInfosBuilderClassType here

    final String buildMethodPrepare = builderInfos.stream()
      .map(BuilderInfo::renderSuperBuilderConstruction)
      .collect(Collectors.joining());

    final String codeBlock;
    final PsiClass superClass = psiClass.getSuperClass();
    if (null != superClass && !"Object".equals(superClass.getName())) {
      codeBlock = "super(b);\n" + buildMethodPrepare;
    }
    else {
      codeBlock = buildMethodPrepare;
    }
    constructorBuilderBased.withBodyText(codeBlock);

    return Optional.of(constructorBuilderBased);
  }

  public Optional<PsiMethod> createBuilderMethodIfNecessary(@NotNull PsiClass containingClass,
                                                            @NotNull PsiClass builderBaseClass,
                                                            @NotNull PsiClass builderImplClass,
                                                            @NotNull PsiAnnotation psiAnnotation,
                                                            @NotNull PsiClassType psiTypeBaseWithGenerics) {
    final String builderMethodName = getBuilderMethodName(psiAnnotation);
    if (builderMethodName.isEmpty() || hasMethod(containingClass, builderMethodName)) {
      return Optional.empty();
    }

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(containingClass.getManager(), builderMethodName)
      .withMethodReturnType(psiTypeBaseWithGenerics)
      .withContainingClass(containingClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PUBLIC)
      .withModifier(PsiModifier.STATIC);
    addTypeParameters(containingClass, null, methodBuilder);

    final String blockText = String.format("return new %s();", PsiClassUtil.getTypeWithGenerics(builderImplClass).getCanonicalText(false));
    methodBuilder.withBodyText(blockText);

    return Optional.of(methodBuilder);
  }

  public static Optional<PsiMethod> createToBuilderMethodIfNecessary(@NotNull PsiClass containingClass,
                                                                     @NotNull PsiClass builderBaseClass,
                                                                     @NotNull PsiClass builderImplClass,
                                                                     @NotNull PsiAnnotation psiAnnotation,
                                                                     @NotNull PsiClassType psiTypeBaseWithGenerics) {
    if (!shouldGenerateToBuilderMethods(psiAnnotation)) {
      return Optional.empty();
    }

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(containingClass.getManager(), TO_BUILDER_METHOD_NAME)
      .withMethodReturnType(psiTypeBaseWithGenerics)
      .withContainingClass(containingClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PUBLIC);

    final String blockText = String.format("return new %s().%s(this);",
                                           PsiClassUtil.getTypeWithGenerics(builderImplClass).getCanonicalText(false),
                                           FILL_VALUES_METHOD_NAME);
    methodBuilder.withBodyText(blockText);

    return Optional.of(methodBuilder);
  }

  private static boolean shouldGenerateToBuilderMethods(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, TO_BUILDER_ANNOTATION_KEY, false);
  }

  private static String selectNonClashingNameFor(String classGenericName, Collection<String> typeParamStrings) {
    String result = classGenericName;
    if (typeParamStrings.contains(classGenericName)) {
      int counter = 2;
      do {
        result = classGenericName + counter++;
      }
      while (typeParamStrings.contains(result));
    }
    return result;
  }

  @NotNull
  public PsiClass createBuilderBaseClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    String builderClassName = getBuilderClassName(psiClass);
    String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final LombokLightClassBuilder baseClassBuilder = new LombokLightClassBuilder(psiClass, builderClassName, builderClassQualifiedName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withParameterTypes(psiClass.getTypeParameterList())
      .withModifier(PsiModifier.PUBLIC)
      .withModifier(PsiModifier.STATIC)
      .withModifier(PsiModifier.ABSTRACT);

    final List<String> typeParamNames = ContainerUtil.map(psiClass.getTypeParameters(), PsiTypeParameter::getName);

    final LightTypeParameterBuilder c = new LightTypeParameterBuilder(selectNonClashingNameFor("C", typeParamNames), baseClassBuilder, 0);
    c.getExtendsList().addReference(PsiClassUtil.getTypeWithGenerics(psiClass));
    baseClassBuilder.withParameterType(c);

    final LightTypeParameterBuilder b = new LightTypeParameterBuilder(selectNonClashingNameFor("B", typeParamNames), baseClassBuilder, 1);
    baseClassBuilder.withParameterType(b);
    b.getExtendsList().addReference(PsiClassUtil.getTypeWithGenerics(baseClassBuilder));

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    final PsiClassType bType = factory.createType(b);
    final PsiClassType cType = factory.createType(c);

    final PsiClass superClass = psiClass.getSuperClass();
    if (null != superClass && !"Object".equals(superClass.getName())) {
      final PsiClass parentBuilderClass = superClass.findInnerClassByName(getBuilderClassName(superClass), false);
      if (null != parentBuilderClass) {
        final PsiType[] explicitTypes = Stream.concat(
            Stream.of(psiClass.getExtendsListTypes()).map(PsiClassType::getParameters).flatMap(Stream::of),
            Stream.of(cType, bType))
          .toArray(PsiType[]::new);

        final PsiClassType extendsType = getTypeWithSpecificTypeParameters(parentBuilderClass, explicitTypes);
        baseClassBuilder.withExtends(extendsType);
      }
    }

    baseClassBuilder.withFieldSupplier((thisPsiClass) -> {
      final List<BuilderInfo> builderInfos = createBuilderInfos(psiClass, psiAnnotation, thisPsiClass);
      initBuilderInfosBuilderClassType(builderInfos, bType);

      // create builder Fields
      return builderInfos.stream()
        .map(BuilderInfo::renderBuilderFields)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
    });

    baseClassBuilder.withMethodSupplier((thisPsiClass) -> {
      final List<BuilderInfo> builderInfos = createBuilderInfos(psiClass, psiAnnotation, thisPsiClass);
      initBuilderInfosBuilderClassType(builderInfos, bType);

      // create all methods
      return addAllMethodsForBaseBuilderClass(psiClass, psiAnnotation, thisPsiClass, builderInfos, bType, cType);
    });

    return baseClassBuilder;
  }

  @NotNull
  private List<BuilderInfo> createBuilderInfos(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation,
                                               @NotNull PsiClass baseClassBuilder) {
    final List<BuilderInfo> builderInfos = createBuilderInfos(psiAnnotation, psiClass, null, baseClassBuilder);
    for (BuilderInfo builderInfo : builderInfos) {
      builderInfo.withBuilderChainResult("self()")
        .withInstanceVariableName(INSTANCE_VARIABLE_NAME);
    }
    return builderInfos;
  }

  private static void initBuilderInfosBuilderClassType(@NotNull List<BuilderInfo> builderInfos, @NotNull PsiClassType bType) {
    for (BuilderInfo builderInfo : builderInfos) {
      builderInfo.withBuilderClassType(bType);
    }
  }

  public Collection<PsiMethod> createAllMethodsOfBaseBuilder(@NotNull PsiClass psiParentClass,
                                                             @NotNull PsiAnnotation psiAnnotation,
                                                             @NotNull PsiClass psiBuilderClass) {
    final PsiTypeParameter[] typeParameters = psiBuilderClass.getTypeParameters();
    final PsiClass bTypeClass, cTypeClass;
    if (typeParameters.length >= 2) {
      bTypeClass = typeParameters[typeParameters.length - 1];
      cTypeClass = typeParameters[typeParameters.length - 2];
    }
    else {
      //Fallback only
      bTypeClass = new LightTypeParameterBuilder("B", psiBuilderClass, 1);
      cTypeClass = new LightTypeParameterBuilder("C", psiBuilderClass, 0);
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiParentClass.getProject());
    final PsiClassType bType = factory.createType(bTypeClass);
    final PsiClassType cType = factory.createType(cTypeClass);

    final List<BuilderInfo> builderInfos = createBuilderInfos(psiParentClass, psiAnnotation, psiBuilderClass);
    initBuilderInfosBuilderClassType(builderInfos, bType);

    // create all methods
    return addAllMethodsForBaseBuilderClass(psiParentClass, psiAnnotation, psiBuilderClass, builderInfos, bType, cType);
  }

  private Collection<PsiMethod> addAllMethodsForBaseBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation,
                                                                 @NotNull PsiClass baseClassBuilder, List<BuilderInfo> builderInfos,
                                                                 @NotNull PsiClassType bType, @NotNull PsiClassType cType) {
    final Collection<PsiMethod> result = new ArrayList<>();

    final Collection<String> existedMethodNames = PsiClassUtil.collectClassMethodsIntern(baseClassBuilder).stream()
      .filter(psiMethod -> PsiAnnotationSearchUtil.isNotAnnotatedWith(psiMethod, LombokClassNames.TOLERATE))
      .map(PsiMethod::getName).collect(Collectors.toSet());

    // create builder methods
    builderInfos.stream()
      .filter(info -> info.notAlreadyExistingMethod(existedMethodNames))
      .map(BuilderInfo::renderBuilderMethods)
      .forEach(result::addAll);

    final PsiManager psiManager = psiClass.getManager();
    final boolean forceCallSuper = PsiClassUtil.hasSuperClass(psiClass);

    if (shouldGenerateToBuilderMethods(psiAnnotation)) {
      // precalculate obtainVia
      builderInfos.forEach(BuilderInfo::withObtainVia);

      if (!existedMethodNames.contains(STATIC_FILL_VALUES_METHOD_NAME)) {
        // create '$fillValuesFromInstanceIntoBuilder' method
        final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, STATIC_FILL_VALUES_METHOD_NAME)
          .withMethodReturnType(PsiTypes.voidType())
          .withParameter(INSTANCE_VARIABLE_NAME, PsiClassUtil.getTypeWithGenerics(psiClass))
          .withParameter(BUILDER_VARIABLE_NAME, getTypeWithWildcardsForSuperBuilderTypeParameters(baseClassBuilder))
          .withContainingClass(baseClassBuilder)
          .withNavigationElement(psiClass)
          .withModifier(PsiModifier.PRIVATE)
          .withModifier(PsiModifier.STATIC);
        addTypeParameters(psiClass, null, methodBuilder);

        final String toBuilderMethodCalls = builderInfos.stream()
          .map(BuilderInfo::renderToBuilderCallWithoutPrependLogic)
          .collect(Collectors.joining(';' + BUILDER_VARIABLE_NAME + '.', BUILDER_VARIABLE_NAME + '.', ";\n"));

        methodBuilder.withBodyText(toBuilderMethodCalls);
        result.add(methodBuilder);
      }

      if (!existedMethodNames.contains(FILL_VALUES_METHOD_NAME)) {
        // create '$fillValuesFrom' method
        final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiManager, FILL_VALUES_METHOD_NAME)
          .withMethodReturnType(bType)
          .withParameter(INSTANCE_VARIABLE_NAME, cType)
          .withContainingClass(baseClassBuilder)
          .withNavigationElement(psiClass)
          .withModifier(PsiModifier.PROTECTED);

        final String callSuperCode = "super." + FILL_VALUES_METHOD_NAME + "(" + INSTANCE_VARIABLE_NAME + ");\n";
        final String codeBlockText = String.format("%s%s.%s(%s, this);\nreturn self();", forceCallSuper ? callSuperCode : "",
                                                   baseClassBuilder.getQualifiedName(), STATIC_FILL_VALUES_METHOD_NAME, INSTANCE_VARIABLE_NAME);
        methodBuilder.withBodyText(codeBlockText);

        result.add(methodBuilder);
      }
    }

    if (!existedMethodNames.contains(SELF_METHOD)) {
      // create 'self' method
      final LombokLightMethodBuilder selfMethod = new LombokLightMethodBuilder(psiManager, SELF_METHOD)
        .withMethodReturnType(bType)
        .withContainingClass(baseClassBuilder)
        .withNavigationElement(psiClass)
        .withModifier(PsiModifier.ABSTRACT)
        .withModifier(PsiModifier.PROTECTED);
      result.add(selfMethod);
    }

    final String buildMethodName = getBuildMethodName(psiAnnotation);
    if (!existedMethodNames.contains(buildMethodName)) {
      // create 'build' method
      final LombokLightMethodBuilder buildMethod = new LombokLightMethodBuilder(psiManager, buildMethodName)
        .withMethodReturnType(cType)
        .withContainingClass(baseClassBuilder)
        .withNavigationElement(psiClass)
        .withModifier(PsiModifier.ABSTRACT)
        .withModifier(PsiModifier.PUBLIC);
      result.add(buildMethod);
    }

    if (!existedMethodNames.contains(ToStringProcessor.TO_STRING_METHOD_NAME)) {
      // create 'toString' method
      result.add(createToStringMethod(psiAnnotation, baseClassBuilder, forceCallSuper));
    }

    return result;
  }

  @NotNull
  public PsiClass createBuilderImplClass(@NotNull PsiClass psiClass, @NotNull PsiClass psiBaseBuilderClass, PsiAnnotation psiAnnotation) {
    String builderClassName = getBuilderImplClassName(psiClass);
    String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final LombokLightClassBuilder implClassBuilder = new LombokLightClassBuilder(psiClass, builderClassName, builderClassQualifiedName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withParameterTypes(psiClass.getTypeParameterList())
      .withModifier(PsiModifier.STATIC)
      .withModifier(PsiModifier.FINAL);

    final PsiClassType extendsType = getTypeWithSpecificTypeParameters(psiBaseBuilderClass,
                                                                       PsiClassUtil.getTypeWithGenerics(psiClass),
                                                                       PsiClassUtil.getTypeWithGenerics(implClassBuilder));
    implClassBuilder.withExtends(extendsType);

    if (hasValidJacksonizedAnnotation(psiClass, null)) {
      handleJacksonized(psiClass, null, psiAnnotation, implClassBuilder);
    }
    else {
      implClassBuilder.withModifier(PsiModifier.PRIVATE);
    }

    implClassBuilder.withMethodSupplier((thisPsiClass) -> createAllMethodsOfImplBuilder(psiClass, psiAnnotation, thisPsiClass));

    return implClassBuilder;
  }

  public Collection<PsiMethod> createAllMethodsOfImplBuilder(@NotNull PsiClass psiClass,
                                                             @NotNull PsiAnnotation psiAnnotation,
                                                             @NotNull PsiClass implBuilderClass) {
    Collection<PsiMethod> result = new ArrayList<>();

    final Collection<String> existedMethodNames =
      ContainerUtil.map2Set(PsiClassUtil.collectClassMethodsIntern(implBuilderClass), PsiMethod::getName);

    final String builderImplClassName = StringUtil.notNullize(implBuilderClass.getName());
    final PsiManager psiManager = psiClass.getManager();
    if (!existedMethodNames.contains(builderImplClassName)) {
      //create private no args constructor
      final LombokLightMethodBuilder privateConstructor = new LombokLightMethodBuilder(psiManager, builderImplClassName)
        .withConstructor(true)
        .withContainingClass(implBuilderClass)
        .withNavigationElement(psiClass)
        .withModifier(PsiModifier.PRIVATE)
        .withBodyText("");
      result.add(privateConstructor);
    }

    if (!existedMethodNames.contains(SELF_METHOD)) {
      // create 'self' method
      final LombokLightMethodBuilder selfMethod = new LombokLightMethodBuilder(psiManager, SELF_METHOD)
        .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(implBuilderClass))
        .withContainingClass(implBuilderClass)
        .withNavigationElement(psiClass)
        .withModifier(PsiModifier.PROTECTED)
        .withBodyText("return this;");
      result.add(selfMethod);
    }

    final String buildMethodName = getBuildMethodName(psiAnnotation);
    if (!existedMethodNames.contains(buildMethodName)) {
      // create 'build' method
      final PsiType builderType = getReturnTypeOfBuildMethod(psiClass, null);
      final PsiSubstitutor builderSubstitutor = getBuilderSubstitutor(psiClass, implBuilderClass);
      final PsiType returnType = builderSubstitutor.substitute(builderType);

      final LombokLightMethodBuilder buildMethod = new LombokLightMethodBuilder(psiManager, buildMethodName)
        .withMethodReturnType(returnType)
        .withContainingClass(implBuilderClass)
        .withNavigationElement(psiClass)
        .withModifier(PsiModifier.PUBLIC);
      final String buildCodeBlockText =
        String.format("return new %s(this);", PsiClassUtil.getTypeWithGenerics(psiClass).getCanonicalText(false));
      buildMethod.withBodyText(buildCodeBlockText);
      result.add(buildMethod);
    }

    return result;
  }

  @NotNull
  public static PsiClassType getTypeWithWildcardsForSuperBuilderTypeParameters(@NotNull PsiClass psiClass) {
    final PsiWildcardType wildcardType = PsiWildcardType.createUnbounded(psiClass.getManager());
    return getTypeWithSpecificTypeParameters(psiClass, wildcardType, wildcardType);
  }

  @NotNull
  private static PsiClassType getTypeWithSpecificTypeParameters(@NotNull PsiClass psiClass, PsiType @NotNull ... psiTypes) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    final PsiTypeParameter[] classTypeParameters = psiClass.getTypeParameters();
    final int substituteTypesCount = psiTypes.length;
    if (classTypeParameters.length >= substituteTypesCount) {
      PsiSubstitutor newSubstitutor = PsiSubstitutor.EMPTY;

      final int fromIndex = classTypeParameters.length - substituteTypesCount;
      for (int i = 0; i < fromIndex; i++) {
        newSubstitutor = newSubstitutor.put(classTypeParameters[i], elementFactory.createType(classTypeParameters[i]));
      }
      for (int i = fromIndex; i < classTypeParameters.length; i++) {
        newSubstitutor = newSubstitutor.put(classTypeParameters[i], psiTypes[i - fromIndex]);
      }
      return elementFactory.createType(psiClass, newSubstitutor);
    }
    return elementFactory.createType(psiClass);
  }
}
