package de.plushnikov.intellij.lombok.processor.method;

import com.intellij.psi.*;
import de.plushnikov.intellij.lombok.ErrorMessages;
import de.plushnikov.intellij.lombok.LombokUtils;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.lombok.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.lombok.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.util.BuilderUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import lombok.experimental.Builder;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BuilderMethodInnerClassProcessor extends AbstractLombokMethodProcessor {

  public static final String METHOD_NAME = "getInstance";

  public BuilderMethodInnerClassProcessor() {
    super(Builder.class, PsiClass.class);
  }

    @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRigthType(psiMethod, builder);
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (!psiMethod.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
      builder.addError(ErrorMessages.canBeUsedOnStaticMethodOnly(Builder.class));
      result = false;
    }
    return result;
  }

  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    PsiClass parentClass = psiMethod.getContainingClass();
    assert parentClass != null;
    final String innerClassSimpleName = BuilderUtil.createBuilderClassName(psiAnnotation, psiMethod.getReturnType());
    final String innerClassCanonicalName = parentClass.getName() + "." + innerClassSimpleName;
    LombokLightClassBuilder innerClass = LombokPsiElementFactory.getInstance().createLightClass(psiMethod.getManager(), innerClassCanonicalName, innerClassSimpleName)
       .withContainingClass(parentClass)
       .withParameterTypes(psiMethod.getTypeParameterList()) // TODO
       .withModifier(PsiModifier.PUBLIC)
       .withModifier(PsiModifier.STATIC);
    innerClass.withConstructors(createConstructors(innerClass, psiAnnotation))
     .withFields(createFields(psiMethod))
     .withMethods(createMethods(parentClass, innerClass, psiMethod, psiAnnotation));

    target.add(innerClass);
  }

  private Collection<PsiMethod> createConstructors(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    NoArgsConstructorProcessor noArgsConstructorProcessor = new NoArgsConstructorProcessor();
    return noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation);
  }

  private Collection<PsiField> createFields(@NotNull PsiMethod psiMethod) {
    List<PsiField> fields = new ArrayList<PsiField>();
    for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
      fields.add(LombokPsiElementFactory.getInstance().createLightField(psiMethod.getManager(), psiParameter.getName(), psiParameter.getType())
        .withModifier(PsiModifier.PRIVATE));
    }
    return fields;
  }

  private Collection<PsiMethod> createMethods(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    methods.addAll(createSetterMethods(innerClass, psiMethod, psiAnnotation));
    methods.add(createBuildMethod(parentClass, innerClass, psiMethod, psiAnnotation));
    methods.addAll(new ToStringProcessor().createToStringMethod(innerClass, parentClass));
    return methods;
  }

  private Collection<PsiMethod> createSetterMethods(@NotNull PsiClass innerClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
      assert psiParameter.getName() != null;
      methods.add(LombokPsiElementFactory.getInstance().createLightMethod(psiMethod.getManager(), BuilderUtil.createSetterName(psiAnnotation, psiParameter.getName()))
        .withMethodReturnType(BuilderUtil.createSetterReturnType(psiAnnotation, PsiClassUtil.getTypeWithGenerics(innerClass)))
        .withContainingClass(innerClass)
        .withParameter(psiParameter.getName(), psiParameter.getType())
        .withNavigationElement(innerClass)
        .withModifier(PsiModifier.PUBLIC));
    }
    return methods;
  }

  private PsiMethod createBuildMethod(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementFactory.getInstance().createLightMethod(parentClass.getManager(), BuilderUtil.createBuildMethodName(psiAnnotation))
       .withMethodReturnType(psiMethod.getReturnType())
       .withContainingClass(innerClass)
       .withNavigationElement(parentClass)
       .withModifier(PsiModifier.PUBLIC);
  }

}
