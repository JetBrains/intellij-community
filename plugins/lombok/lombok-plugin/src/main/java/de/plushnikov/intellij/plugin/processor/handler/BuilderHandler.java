package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
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

  private final ToStringProcessor toStringProcessor = new ToStringProcessor();

  public boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, boolean validateInnerClass, @NotNull ProblemBuilder problemBuilder) {
    final PsiType psiBuilderType = PsiClassUtil.getTypeWithGenerics(psiClass);

    return validateAnnotationOnRightType(psiClass, problemBuilder) && validate(psiClass, psiAnnotation, psiBuilderType, validateInnerClass, problemBuilder);
  }

  private boolean validate(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiType psiBuilderType, boolean validateInnerClass, @NotNull ProblemBuilder problemBuilder) {
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);

    boolean result = validateBuilderClassName(builderClassName, psiAnnotation.getProject(), problemBuilder);
    if (validateInnerClass) {
      result &= validateExistingInnerClass(builderClassName, psiClass, problemBuilder);
    }
    return result;
  }

  protected boolean validateBuilderClassName(@NotNull String builderClassName, @NotNull Project project, @NotNull ProblemBuilder builder) {
    final PsiNameHelper psiNameHelper = JavaPsiFacade.getInstance(project).getNameHelper();
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

  public boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, boolean validateInnerClass, @NotNull ProblemBuilder problemBuilder) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    if (null != psiClass) {
      final PsiType psiBuilderType = getBuilderType(psiMethod, psiClass);

      return validateAnnotationOnRightType(psiMethod, problemBuilder) &&
          validate(psiClass, psiAnnotation, psiBuilderType, validateInnerClass, problemBuilder);
    }
    return false;
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) && !psiMethod.isConstructor()) {
      builder.addError("%s is only supported on types, constructors, and static methods", Builder.class);
      return false;
    }
    return true;
  }

  protected boolean validateExistingInnerClass(@NotNull String builderClassName, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final PsiClass innerBuilderClass = PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);
    if (null != innerBuilderClass) {
      builder.addWarning("Not generated '%s' class: A class with same name already exists. This feature is not implemented at the moment.", builderClassName);
    }
    return null == innerBuilderClass;
  }

  public PsiType getBuilderType(@NotNull PsiMethod psiMethod, @NotNull PsiClass psiClass) {
    final PsiType psiBuilderTargetClass;
    if (psiMethod.isConstructor()) {
      psiBuilderTargetClass = PsiClassUtil.getTypeWithGenerics(psiClass);
    } else {
      psiBuilderTargetClass = psiMethod.getReturnType();
    }
    return psiBuilderTargetClass;
  }

//  @NotNull
//  private String getBuilderClassNameX(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
//    String builderClassName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_CLASS_NAME, String.class);
//    return StringUtils.isNotBlank(builderClassName) ? builderClassName : StringUtils.capitalize(psiClass.getName()) + BUILDER_CLASS_NAME;
//  }

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
  public String getBuilderClassName(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiType psiBuilderType) {
    String builderClassName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_CLASS_NAME, String.class);
    if (StringUtils.isBlank(builderClassName)) {
      if (PsiType.VOID.equals(psiBuilderType)) {
        return StringUtils.capitalize(PsiType.VOID.getCanonicalText()) + BUILDER_CLASS_NAME;
      } else {
        PsiClass psiBuilderClass = PsiTypesUtil.getPsiClass(psiBuilderType);
        psiBuilderClass = null == psiBuilderClass ? psiClass : psiBuilderClass;
        return StringUtils.capitalize(psiBuilderClass.getName()) + BUILDER_CLASS_NAME;
      }
    }
    return builderClassName;
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
  public PsiClass createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    final PsiType psiBuilderType = getBuilderType(psiMethod, psiClass);

    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);

    LombokLightClassBuilder builderClass = createBuilderClass(psiClass, psiMethod, builderClassName, psiAnnotation);
    builderClass.withFields(createFields(psiMethod));
    builderClass.withMethods(createMethods(psiClass, psiAnnotation, psiBuilderType, builderClass));

    return builderClass;
  }

  @NotNull
  public PsiClass createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final PsiType psiBuilderType = PsiClassUtil.getTypeWithGenerics(psiClass);

    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);

    LombokLightClassBuilder builderClass = createBuilderClass(psiClass, psiClass, builderClassName, psiAnnotation);
    builderClass.withFields(createFields(psiClass));
    builderClass.withMethods(createMethods(psiClass, psiAnnotation, psiBuilderType, builderClass));

    return builderClass;
  }

  private Collection<PsiMethod> createMethods(PsiClass psiClass, PsiAnnotation psiAnnotation, PsiType psiBuilderType, LombokLightClassBuilder builderClass) {
    Collection<PsiMethod> psiMethods = new ArrayList<PsiMethod>();
    psiMethods.addAll(createFieldMethods(psiClass, builderClass, psiAnnotation));
    psiMethods.add(createBuildMethod(psiClass, builderClass, psiAnnotation, psiBuilderType));
    psiMethods.addAll(toStringProcessor.createToStringMethod(builderClass, psiAnnotation));
    return psiMethods;
  }

  @NotNull
  protected LombokLightClassBuilder createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiTypeParameterListOwner psiTypeParameterListOwner, @NotNull String builderClassName, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final Project project = psiClass.getProject();
    LombokLightClassBuilder innerClass = new LombokLightClassBuilder(project, builderClassName, builderClassQualifiedName)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withParameterTypes(psiTypeParameterListOwner.getTypeParameterList())
        .withModifier(PsiModifier.PUBLIC)
        .withModifier(PsiModifier.STATIC);

    innerClass.withConstructors(createConstructors(innerClass, psiAnnotation));

    return innerClass;
  }

  protected Collection<PsiMethod> createConstructors(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    NoArgsConstructorProcessor noArgsConstructorProcessor = new NoArgsConstructorProcessor();
    return noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation);
  }

  protected Collection<PsiField> createFields(@NotNull PsiClass psiClass) {
    final PsiManager psiManager = psiClass.getManager();

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
        fields.add(new LombokLightFieldBuilder(psiManager, psiField.getName(), psiField.getType())
            .withModifier(PsiModifier.PRIVATE));
      }
    }
    return fields;
  }

  private Collection<PsiField> createFields(@NotNull PsiMethod psiMethod) {
    final PsiManager psiManager = psiMethod.getManager();
    List<PsiField> fields = new ArrayList<PsiField>();
    for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
      final String parameterName = psiParameter.getName();
      if (null != parameterName) {
        fields.add(
            new LombokLightFieldBuilder(psiManager, parameterName, psiParameter.getType())
                .withModifier(PsiModifier.PRIVATE)
                .withContainingClass(psiMethod.getContainingClass()));
      }
    }
    return fields;
  }

  protected Collection<PsiMethod> createFieldMethods(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (PsiField psiField : innerClass.getFields()) {
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

  protected PsiMethod createBuildMethod(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiType psiBuilderType) {
    return new LombokLightMethodBuilder(parentClass.getManager(), getBuildMethodName(psiAnnotation))
        .withMethodReturnType(psiBuilderType)
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
