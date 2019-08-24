package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class SuperBuilderHandler extends BuilderHandler {

  private static final String SELF_METHOD = "self";
  private static final String TO_BUILDER_METHOD_NAME = "toBuilder";
  private static final String FILL_VALUES_METHOD_NAME = "$fillValuesFrom";
  private static final String STATIC_FILL_VALUES_METHOD_NAME = "$fillValuesFromInstanceIntoBuilder";
  private static final String INSTANCE_VARIABLE_NAME = "instance";
  private static final String BUILDER_VARIABLE_NAME = "b";

  public SuperBuilderHandler(@NotNull ToStringProcessor toStringProcessor, @NotNull NoArgsConstructorProcessor noArgsConstructorProcessor) {
    super(toStringProcessor, noArgsConstructorProcessor);
  }

  @NotNull
  public String getBuilderClassName(@NotNull PsiClass psiClass) {
    return StringUtil.capitalize(psiClass.getName() + BUILDER_CLASS_NAME);
  }

  @NotNull
  public String getBuilderImplClassName(@NotNull PsiClass psiClass) {
    return getBuilderClassName(psiClass) + "Impl";
  }

  public Optional<PsiMethod> createBuilderBasedConstructor(@NotNull PsiClass psiClass, @NotNull PsiClass builderClass, @NotNull PsiAnnotation psiAnnotation) {
    final String className = psiClass.getName();
    if (null == className) {
      return Optional.empty();
    }

    final LightTypeParameterBuilder t1 = new LightTypeParameterBuilder("?", builderClass, 0);
    final LightTypeParameterBuilder t2 = new LightTypeParameterBuilder("?", builderClass, 1);
    final PsiClassType psiTypeBaseWithGenerics = PsiClassUtil.createTypeWithGenerics(builderClass, t1, t2);

    LombokLightMethodBuilder constructorBuilderBased = new LombokLightMethodBuilder(psiClass.getManager(), className)
      .withConstructor(true)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PROTECTED)
      .withParameter(BUILDER_VARIABLE_NAME, psiTypeBaseWithGenerics);

    // TODO add body
    final PsiClass superClass = psiClass.getSuperClass();
    if (null != superClass && !"Object".equals(superClass.getName())) {
      constructorBuilderBased.withBody(PsiMethodUtil.createCodeBlockFromText("super(b);", constructorBuilderBased));
    } else {
      constructorBuilderBased.withBody(PsiMethodUtil.createCodeBlockFromText("", constructorBuilderBased));
    }

    return Optional.of(constructorBuilderBased);
  }

  public Optional<PsiMethod> createBuilderMethodIfNecessary(@NotNull PsiClass containingClass,
                                                            @NotNull PsiClass builderBaseClass,
                                                            @NotNull PsiClass builderImplClass,
                                                            @NotNull PsiAnnotation psiAnnotation) {
    final String builderMethodName = getBuilderMethodName(psiAnnotation);
    if (!builderMethodName.isEmpty() && !hasMethod(containingClass, builderMethodName)) {

      final LightTypeParameterBuilder t1 = new LightTypeParameterBuilder("?", builderBaseClass, 0);
      final LightTypeParameterBuilder t2 = new LightTypeParameterBuilder("?", builderBaseClass, 1);
      final PsiClassType psiTypeBaseWithGenerics = PsiClassUtil.createTypeWithGenerics(builderBaseClass, t1, t2);

      final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(containingClass.getManager(), builderMethodName)
        .withMethodReturnType(psiTypeBaseWithGenerics)
        .withContainingClass(containingClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(getBuilderOuterAccessVisibility(psiAnnotation))
        .withModifier(PsiModifier.STATIC);

      final String blockText = String.format("return new %s();", builderImplClass.getName());
      methodBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(blockText, methodBuilder));

      return Optional.of(methodBuilder);
    }
    return Optional.empty();
  }

  public Optional<PsiMethod> createToBuilderMethodIfNecessary(@NotNull PsiClass containingClass, @NotNull PsiClass builderPsiClass, @NotNull PsiAnnotation psiAnnotation) {
    //TODO implement this
    return super.createToBuilderMethodIfNecessary(containingClass, null, builderPsiClass, psiAnnotation);
  }

  @NotNull
  public PsiClass createBuilderBaseClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    String builderClassName = getBuilderClassName(psiClass);
    String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final LombokLightClassBuilder baseClassBuilder = new LombokLightClassBuilder(psiClass, builderClassName, builderClassQualifiedName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withParameterTypes(psiClass.getTypeParameterList())
      .withModifier(getBuilderOuterAccessVisibility(psiAnnotation))
      .withModifier(PsiModifier.ABSTRACT)
      .withModifier(PsiModifier.STATIC);

    final LightTypeParameterBuilder c = new LightTypeParameterBuilder("C", baseClassBuilder, 0);
    c.getExtendsList().addReference(psiClass);
    baseClassBuilder.withParameterType(c);

    final LightTypeParameterBuilder b = new LightTypeParameterBuilder("B", baseClassBuilder, 1);
    baseClassBuilder.withParameterType(b);
    b.getExtendsList().addReference(PsiClassUtil.createTypeWithGenerics(baseClassBuilder, c, b));//TODO make this work

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    final PsiClassType bType = factory.createType(b);
    final PsiClassType cType = factory.createType(c);

    final PsiClass superClass = psiClass.getSuperClass();
    if (null != superClass && !"Object".equals(superClass.getName())) {
      final PsiClass parentBuilderClass = superClass.findInnerClassByName(getBuilderClassName(superClass), false);
      if (null != parentBuilderClass) {
        baseClassBuilder.withExtends(PsiClassUtil.createTypeWithGenerics(parentBuilderClass, c, b));
      }
    }

    final List<BuilderInfo> builderInfos = createBuilderInfos(psiAnnotation, psiClass, null, baseClassBuilder);
    for (BuilderInfo builderInfo : builderInfos) {
      builderInfo.withBuilderClassType(bType);
    }

    // create builder Fields
    builderInfos.stream()
      .map(BuilderInfo::renderBuilderFields)
      .forEach(baseClassBuilder::withFields);

    // create builder methods
    builderInfos.stream()
      //TODO change "return this;" to "return self();"
      .map(BuilderInfo::renderBuilderMethods)
      .forEach(baseClassBuilder::withMethods);

    // create 'self' method ( protected abstract B self(); )
    final LombokLightMethodBuilder selfMethod = new LombokLightMethodBuilder(psiClass.getManager(), SELF_METHOD)
      .withMethodReturnType(bType)
      .withContainingClass(baseClassBuilder)
      .withNavigationElement(psiClass)
      .withModifier(PsiModifier.ABSTRACT)
      .withModifier(PsiModifier.PROTECTED);
    baseClassBuilder.addMethod(selfMethod);

    // create 'build' method ( public abstract C build(); )
    final String buildMethodName = getBuildMethodName(psiAnnotation);
    final LombokLightMethodBuilder buildMethod = new LombokLightMethodBuilder(psiClass.getManager(), buildMethodName)
      .withMethodReturnType(cType)
      .withContainingClass(baseClassBuilder)
      .withNavigationElement(psiClass)
      .withModifier(PsiModifier.ABSTRACT)
      .withModifier(PsiModifier.PUBLIC);
    baseClassBuilder.addMethod(buildMethod);

    // create 'toString' method
    baseClassBuilder.addMethod(createToStringMethod(psiAnnotation, baseClassBuilder));

    return baseClassBuilder;
  }

  @NotNull
  public PsiClass createBuilderImplClass(@NotNull PsiClass psiClass, @NotNull PsiClass psiBaseBuilderClass, PsiAnnotation psiAnnotation) {
    String builderClassName = getBuilderImplClassName(psiClass);
    String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final LombokLightClassBuilder implClassBuilder = new LombokLightClassBuilder(psiClass, builderClassName, builderClassQualifiedName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withParameterTypes(psiClass.getTypeParameterList())
      .withModifier(getBuilderOuterAccessVisibility(psiAnnotation))
      .withModifier(PsiModifier.STATIC);

    implClassBuilder.withExtends(PsiClassUtil.createTypeWithGenerics(psiBaseBuilderClass, psiClass, implClassBuilder));

    // create 'self' method
    final LombokLightMethodBuilder selfMethod = new LombokLightMethodBuilder(psiClass.getManager(), SELF_METHOD)
      .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(implClassBuilder))//todo without generics?
      .withContainingClass(implClassBuilder)
      .withNavigationElement(psiClass)
      .withModifier(PsiModifier.PROTECTED);
    selfMethod.withBody(PsiMethodUtil.createCodeBlockFromText("return this;", selfMethod));
    implClassBuilder.addMethod(selfMethod);

    // create 'build' method
    final PsiType builderType = getReturnTypeOfBuildMethod(psiClass, null);
    final PsiSubstitutor builderSubstitutor = getBuilderSubstitutor(psiClass, implClassBuilder);
    final PsiType returnType = builderSubstitutor.substitute(builderType);

    final String buildMethodName = getBuildMethodName(psiAnnotation);
    final LombokLightMethodBuilder buildMethod = new LombokLightMethodBuilder(psiClass.getManager(), buildMethodName)
      .withMethodReturnType(returnType)
      .withContainingClass(implClassBuilder)
      .withNavigationElement(psiClass)
      .withModifier(PsiModifier.PUBLIC);
    final String buildCodeBlockText = String.format("return new %s(this);", psiClass.getName());
    buildMethod.withBody(PsiMethodUtil.createCodeBlockFromText(buildCodeBlockText, buildMethod));
    implClassBuilder.addMethod(buildMethod);

    // create 'toString' method
    implClassBuilder.addMethod(createToStringMethod(psiAnnotation, implClassBuilder));

    return implClassBuilder;
  }
}
