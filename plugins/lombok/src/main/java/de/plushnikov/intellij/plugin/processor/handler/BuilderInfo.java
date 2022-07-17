package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.handler.singular.BuilderElementHandler;
import de.plushnikov.intellij.plugin.processor.handler.singular.SingularHandlerFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class BuilderInfo {
  private static final String BUILDER_OBTAIN_VIA_FIELD = "field";
  private static final String BUILDER_OBTAIN_VIA_METHOD = "method";
  private static final String BUILDER_OBTAIN_VIA_STATIC = "isStatic";
  private static final String BUILDER_OBTAIN_VIA_ANNOTATION = LombokClassNames.BUILDER_OBTAIN_VIA;
  private static final String BUILDER_DEFAULT_ANNOTATION = LombokClassNames.BUILDER_DEFAULT;

  private PsiVariable variableInClass;
  private PsiType fieldInBuilderType;
  private boolean deprecated;
  private String visibilityModifier;
  private String setterPrefix;

  private String builderChainResult = "this";

  private PsiClass builderClass;
  private PsiType builderClassType;

  private String fieldInBuilderName;
  private PsiExpression fieldInitializer;
  private boolean hasBuilderDefaultAnnotation;

  private PsiAnnotation singularAnnotation;
  private BuilderElementHandler builderElementHandler;

  private PsiAnnotation obtainViaAnnotation;
  private String viaFieldName;
  private String viaMethodName;
  private boolean viaStaticCall;
  private String instanceVariableName = "this";

  public static BuilderInfo fromPsiParameter(@NotNull PsiParameter psiParameter) {
    final BuilderInfo result = new BuilderInfo();

    result.variableInClass = psiParameter;
    result.fieldInBuilderType = psiParameter.getType();
    result.deprecated = hasDeprecatedAnnotation(psiParameter);
    result.fieldInitializer = null;
    result.hasBuilderDefaultAnnotation = false;

    result.fieldInBuilderName = psiParameter.getName();

    result.singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiParameter, LombokClassNames.SINGULAR);
    result.builderElementHandler = SingularHandlerFactory.getHandlerFor(psiParameter, null!=result.singularAnnotation);

    return result;
  }

  private static boolean hasDeprecatedAnnotation(@NotNull PsiModifierListOwner modifierListOwner) {
    return PsiAnnotationSearchUtil.isAnnotatedWith(modifierListOwner, Deprecated.class.getName());
  }

  public static BuilderInfo fromPsiField(@NotNull PsiField psiField) {
    final BuilderInfo result = new BuilderInfo();

    result.variableInClass = psiField;
    result.deprecated = isDeprecated(psiField);
    result.fieldInBuilderType = psiField.getType();
    result.fieldInitializer = psiField.getInitializer();
    result.hasBuilderDefaultAnnotation = PsiAnnotationSearchUtil.isAnnotatedWith(psiField, BUILDER_DEFAULT_ANNOTATION);

    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
    result.fieldInBuilderName = accessorsInfo.removePrefix(psiField.getName());

    result.singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, LombokClassNames.SINGULAR);
    result.builderElementHandler = SingularHandlerFactory.getHandlerFor(psiField, null!=result.singularAnnotation);

    return result;
  }

  public static BuilderInfo fromPsiRecordComponent(@NotNull PsiRecordComponent psiRecordComponent) {
    final BuilderInfo result = new BuilderInfo();

    result.variableInClass = psiRecordComponent;
    result.deprecated = hasDeprecatedAnnotation(psiRecordComponent);
    result.fieldInBuilderType = psiRecordComponent.getType();
    result.fieldInitializer = psiRecordComponent.getInitializer();
    result.hasBuilderDefaultAnnotation = PsiAnnotationSearchUtil.isAnnotatedWith(psiRecordComponent, BUILDER_DEFAULT_ANNOTATION);

    result.fieldInBuilderName = psiRecordComponent.getName();

    result.singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiRecordComponent, LombokClassNames.SINGULAR);
    result.builderElementHandler = SingularHandlerFactory.getHandlerFor(psiRecordComponent, null!=result.singularAnnotation);

    return result;
  }

  private static boolean isDeprecated(@NotNull PsiField psiField) {
    return PsiImplUtil.isDeprecatedByDocTag(psiField) || hasDeprecatedAnnotation(psiField);
  }

  public BuilderInfo withSubstitutor(@NotNull PsiSubstitutor builderSubstitutor) {
    fieldInBuilderType = builderSubstitutor.substitute(fieldInBuilderType);
    return this;
  }

  public BuilderInfo withVisibilityModifier(String visibilityModifier) {
    this.visibilityModifier = visibilityModifier;
    return this;
  }

  public BuilderInfo withSetterPrefix(String setterPrefix) {
    this.setterPrefix = setterPrefix;
    return this;
  }

  public BuilderInfo withBuilderClass(@NotNull PsiClass builderClass) {
    this.builderClass = builderClass;
    this.builderClassType = PsiClassUtil.getTypeWithGenerics(builderClass);
    return this;
  }

  public BuilderInfo withBuilderClassType(@NotNull PsiClassType builderClassType) {
    this.builderClassType = builderClassType;
    return this;
  }

  public BuilderInfo withBuilderChainResult(@NotNull String builderChainResult) {
    this.builderChainResult = builderChainResult;
    return this;
  }

  public BuilderInfo withObtainVia() {
    obtainViaAnnotation = PsiAnnotationSearchUtil.findAnnotation(variableInClass, BUILDER_OBTAIN_VIA_ANNOTATION);
    if (null != obtainViaAnnotation) {
      viaFieldName = PsiAnnotationUtil.getStringAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_FIELD, "");
      viaMethodName = PsiAnnotationUtil.getStringAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_METHOD, "");
      viaStaticCall = PsiAnnotationUtil.getBooleanAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_STATIC, false);
    }
    return this;
  }

  public boolean useForBuilder() {
    boolean result = true;

    PsiModifierList modifierList = variableInClass.getModifierList();
    if (null != modifierList) {
      //Skip static fields.
      result = !modifierList.hasModifierProperty(PsiModifier.STATIC);

      // skip initialized final fields unless annotated with @Builder.Default
      final boolean isInitializedFinalField = null != fieldInitializer && modifierList.hasModifierProperty(PsiModifier.FINAL);
      if (isInitializedFinalField && !hasBuilderDefaultAnnotation) {
        result = false;
      }
    }

    //Skip fields that start with $
    result &= !fieldInBuilderName.startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);

    return result;
  }

  public boolean notAlreadyExistingField(Collection<String> alreadyExistingFieldNames) {
    return !alreadyExistingFieldNames.contains(fieldInBuilderName);
  }

  public boolean notAlreadyExistingMethod(Collection<String> alreadyExistedMethodNames) {
    return !alreadyExistedMethodNames.contains(calcBuilderMethodName());
  }

  public Project getProject() {
    return variableInClass.getProject();
  }

  public PsiManager getManager() {
    return variableInClass.getManager();
  }

  public String getFieldName() {
    return fieldInBuilderName;
  }

  public PsiType getFieldType() {
    return fieldInBuilderType;
  }

  public PsiVariable getVariable() {
    return variableInClass;
  }

  public boolean isDeprecated() {
    return deprecated;
  }

  @PsiModifier.ModifierConstant
  public String getVisibilityModifier() {
    return visibilityModifier;
  }

  public String getSetterPrefix() {
    return setterPrefix;
  }

  public PsiClass getBuilderClass() {
    return builderClass;
  }

  public PsiType getBuilderType() {
    return builderClassType;
  }

  public String getBuilderChainResult() {
    return builderChainResult;
  }

  public PsiAnnotation getSingularAnnotation() {
    return singularAnnotation;
  }

  public boolean hasSingularAnnotation() {
    return null != singularAnnotation;
  }

  public boolean hasBuilderDefaultAnnotation() {
    return hasBuilderDefaultAnnotation;
  }

  public boolean hasNoInitializer() {
    return null == fieldInitializer;
  }

  public boolean hasObtainViaAnnotation() {
    return null != obtainViaAnnotation;
  }

  public PsiExpression getFieldInitializer() {
    return fieldInitializer;
  }

  public String getViaFieldName() {
    return viaFieldName;
  }

  public String getViaMethodName() {
    return viaMethodName;
  }

  public boolean isViaStaticCall() {
    return viaStaticCall;
  }

  public String getInstanceVariableName() {
    return instanceVariableName;
  }

  public Collection<String> getAnnotations() {
    if (deprecated) {
      return Collections.singleton(CommonClassNames.JAVA_LANG_DEPRECATED);
    }
    return Collections.emptyList();
  }

  public Collection<PsiField> renderBuilderFields() {
    return builderElementHandler.renderBuilderFields(this);
  }

  private String calcBuilderMethodName() {
    return builderElementHandler.calcBuilderMethodName(this);
  }

  public Collection<PsiMethod> renderBuilderMethods() {
    return builderElementHandler.renderBuilderMethod(this);
  }

  public String renderBuildPrepare() {
    return builderElementHandler.renderBuildPrepare(this);
  }

  public String renderSuperBuilderConstruction() {
    return builderElementHandler.renderSuperBuilderConstruction(variableInClass, fieldInBuilderName);
  }

  public String renderBuildCall() {
    return renderFieldName();
  }

  public String renderFieldName() {
    return hasBuilderDefaultAnnotation ? fieldInBuilderName + "$value" : fieldInBuilderName;
  }

  public String renderFieldDefaultSetName() {
    return hasBuilderDefaultAnnotation ? fieldInBuilderName + "$set" : null;
  }

  public String renderFieldDefaultProviderName() {
    return hasBuilderDefaultAnnotation ? "$default$" + fieldInBuilderName : null;
  }

  public CharSequence renderToBuilderCall() {
    if (hasObtainViaAnnotation()) {
      final StringBuilder result = new StringBuilder();
      result.append(fieldInBuilderName);
      result.append('(');
      if (StringUtil.isNotEmpty(viaFieldName)) {
        result.append(instanceVariableName).append(".").append(viaFieldName);
      } else if (StringUtil.isNotEmpty(viaMethodName)) {

        result.append(viaStaticCall ? getPsiClass().getName() : instanceVariableName);
        result.append('.');
        result.append(viaMethodName);
        result.append(viaStaticCall ? "(" + instanceVariableName + ")" : "()");
      } else {
        result.append(instanceVariableName).append(".").append(variableInClass.getName());
      }
      result.append(')');
      return result;
    } else {
      return builderElementHandler.renderToBuilderCall(this);
    }
  }

  private PsiClass getPsiClass() {
    return builderClass.getContainingClass();
  }

  public Optional<PsiType> getObtainViaFieldVariableType() {
    PsiVariable psiVariable = variableInClass;

    if (StringUtil.isNotEmpty(viaFieldName)) {
      final PsiField fieldByName = getPsiClass().findFieldByName(viaFieldName, false);
      if (fieldByName != null) {
        psiVariable = fieldByName;
      }
    }

    final PsiType psiVariableType = psiVariable.getType();

    if (psiVariableType instanceof PsiClassReferenceType) {
      final PsiClass resolvedPsiVariableClass = ((PsiClassReferenceType) psiVariableType).resolve();
      if (resolvedPsiVariableClass instanceof PsiTypeParameter) {
        return Optional.of(psiVariableType);
      }
    }
    return Optional.empty();
  }

  public void withInstanceVariableName(String instanceVariableName) {
    this.instanceVariableName = instanceVariableName;
  }
}
