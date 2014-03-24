package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.ErrorMessages;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.Builder;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BuilderHandler {
  private final static String ANNOTATION_BUILDER_CLASS_NAME = "builderClassName";
  public static final String ANNOTATION_BUILD_METHOD_NAME = "buildMethodName";
  public static final String ANNOTATION_BUILDER_METHOD_NAME = "builderMethodName";

  private final static String BUILDER_CLASS_NAME = "Builder";
  private final static String BUILD_METHOD_NAME = "build";
  private final static String BUILDER_METHOD_NAME = "builder";

  public boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder problemBuilder) {
    return validateBuilderClassName(psiClass, psiAnnotation, problemBuilder) &&
        validateAnnotationOnRightType(psiClass, problemBuilder)
        && validateExistingInnerClass(psiClass, psiAnnotation, problemBuilder);
  }

  protected boolean validateBuilderClassName(@NotNull PsiClass psiClass, PsiAnnotation psiAnnotation, ProblemBuilder builder) {
    final Project project = psiAnnotation.getProject();
    final PsiNameHelper psiNameHelper = JavaPsiFacade.getInstance(project).getNameHelper();
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation);
    if (!psiNameHelper.isIdentifier(builderClassName)) {
      builder.addError("%s ist not a valid identifier", builderClassName);
      return false;
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

  public boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder problemBuilder) {
    return validateAnnotationOnRightType(psiMethod, problemBuilder) && validateExistingInnerClass(psiMethod, psiAnnotation, problemBuilder);
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) && !psiMethod.isConstructor()) {
      builder.addError(ErrorMessages.canBeUsedOnStaticMethodOnly(Builder.class));
      return false;
    }
    return true;
  }

  protected boolean validateExistingInnerClass(@NotNull PsiMember psiMember, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder builder) {
    final PsiClass containingClass = psiMember.getContainingClass();
    if (null != containingClass) {
      final String builderClassName = getBuilderClassName(containingClass, psiAnnotation);
      final PsiClass innerBuilderClass = PsiClassUtil.getInnerClassInternByName(containingClass, builderClassName);
      if (null != innerBuilderClass) {
        builder.addError("Not generated '%s' class: A class with same name already exists. This feature is not implemented at the moment.", builderClassName);
        return false;
      }
    }
    return true;
  }

  public String getBuilderClassName(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    String builderClassName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_CLASS_NAME, String.class);
    return StringUtils.isNotBlank(builderClassName) ? builderClassName : StringUtils.capitalize(psiClass.getName()) + BUILDER_CLASS_NAME;
  }

  @NotNull
  public static String getBuildMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String buildMethodName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_BUILD_METHOD_NAME, String.class);
    return StringUtils.isNotBlank(buildMethodName) ? buildMethodName : BUILD_METHOD_NAME;
  }

  @NotNull
  public static String getBuilderMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String builderMethodName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_METHOD_NAME, String.class);
    return StringUtils.isNotBlank(builderMethodName) ? builderMethodName : BUILDER_METHOD_NAME;
  }

  @NotNull
  public PsiMethod createBuilderMethod(@NotNull PsiClass containingClass, @NotNull PsiClass builderPsiClass, @NotNull PsiAnnotation psiAnnotation) {
    return new LombokLightMethodBuilder(containingClass.getManager(), getBuilderMethodName(psiAnnotation))
        .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(builderPsiClass))
        .withContainingClass(containingClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(PsiModifier.PUBLIC)
        .withModifier(PsiModifier.STATIC);
  }

  @NotNull
  public PsiClass createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation);
    final String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    LombokLightClassBuilder innerClass = new LombokLightClassBuilder(psiClass.getProject(), builderClassName, builderClassQualifiedName)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withParameterTypes(psiClass.getTypeParameterList())
        .withModifier(PsiModifier.PUBLIC)
        .withModifier(PsiModifier.STATIC);

    innerClass.withConstructors(createConstructors(innerClass, psiAnnotation))
        .withFields(createFields(psiClass))
        .withMethods(createMethods(psiClass, innerClass, psiAnnotation));

    return innerClass;
  }

  protected Collection<PsiMethod> createConstructors(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    NoArgsConstructorProcessor noArgsConstructorProcessor = new NoArgsConstructorProcessor();
    return noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation);
  }

  protected Collection<PsiField> createFields(@NotNull PsiClass psiClass) {
    List<PsiField> fields = new ArrayList<PsiField>();
    for (PsiField psiField : psiClass.getFields()) {
      boolean createField = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        createField = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields that start with $
        createField &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        // skip initialized final fields
        createField &= !(null != psiField.getInitializer() && modifierList.hasModifierProperty(PsiModifier.FINAL));
      }
      if (createField) {
        fields.add(new LombokLightFieldBuilder(psiClass.getManager(), psiField.getName(), psiField.getType())
            .withModifier(PsiModifier.PRIVATE));
      }
    }
    return fields;
  }

  protected Collection<PsiMethod> createMethods(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    methods.addAll(createFieldMethods(parentClass, innerClass, psiAnnotation));
    methods.add(createBuildMethod(parentClass, innerClass, psiAnnotation));
    methods.addAll(new ToStringProcessor().createToStringMethod(innerClass, psiAnnotation));
    return methods;
  }

  protected Collection<PsiMethod> createFieldMethods(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (PsiField psiField : parentClass.getFields()) {
      boolean createMethod = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        createMethod = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields that start with $
        createMethod &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        // skip initialized final fields
        createMethod &= !(null != psiField.getInitializer() && modifierList.hasModifierProperty(PsiModifier.FINAL));
      }
      if (createMethod) {
        SetterFieldProcessor setterFieldProcessor = new SetterFieldProcessor();
        setterFieldProcessor.createSetterMethod(psiField, PsiModifier.PUBLIC);
        methods.add(new LombokLightMethodBuilder(psiField.getManager(), createSetterName(psiAnnotation, psiField.getName()))
            .withMethodReturnType(createSetterReturnType(psiAnnotation, PsiClassUtil.getTypeWithGenerics(innerClass)))
            .withContainingClass(innerClass)
            .withParameter(psiField.getName(), psiField.getType())
            .withNavigationElement(psiAnnotation)
            .withModifier(PsiModifier.PUBLIC));
      }
    }
    return methods;
  }

  protected PsiMethod createBuildMethod(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation) {
    return new LombokLightMethodBuilder(parentClass.getManager(), getBuildMethodName(psiAnnotation))
        .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(parentClass))
        .withContainingClass(innerClass)
        .withNavigationElement(parentClass)
        .withModifier(PsiModifier.PUBLIC);
  }

  public static final String ANNOTATION_FLUENT = "fluent";
  public static final String ANNOTATION_CHAIN = "chain";

  public final static String SETTER_PREFIX = "set";

  @NotNull
  private String createSetterName(@NotNull PsiAnnotation psiAnnotation, @NotNull String fieldName) {
    Boolean fluentAnnotationValue = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_FLUENT, Boolean.class);
    final boolean isFluent = fluentAnnotationValue != null ? fluentAnnotationValue : true;
    return isFluent ? fieldName : SETTER_PREFIX + StringUtils.capitalize(fieldName);
  }

  @NotNull
  private PsiType createSetterReturnType(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiType fieldType) {
    Boolean chainAnnotationValue = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_CHAIN, Boolean.class);
    final boolean isChain = chainAnnotationValue != null ? chainAnnotationValue : true;
    return isChain ? fieldType : PsiType.VOID;
  }
}
