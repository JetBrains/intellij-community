package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.handler.singular.BuilderElementHandler;
import de.plushnikov.intellij.plugin.processor.handler.singular.SingularHandlerFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.util.StringUtils;
import lombok.Builder;
import lombok.Singular;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

public class BuilderInfo {
  private static final String BUILDER_OBTAIN_VIA_FIELD = "field";
  private static final String BUILDER_OBTAIN_VIA_METHOD = "method";
  private static final String BUILDER_OBTAIN_VIA_STATIC = "isStatic";
  private static final String BUILDER_OBTAIN_VIA_ANNOTATION = Builder.ObtainVia.class.getName().replace("$", ".");
  private static final String BUILDER_DEFAULT_ANNOTATION = Builder.Default.class.getName().replace("$", ".");

  private PsiVariable variableInClass;
  private PsiType fieldInBuilderType;

  private PsiClass builderClass;
  private PsiType builderClassType;

  private String fieldInBuilderName;
  private PsiExpression fieldInitializer;
  private boolean hasBuilderDefaultAnnotation;

  private PsiAnnotation singularAnnotation;
  private BuilderElementHandler builderElementHandler;

  private boolean fluentBuilder = true;
  private boolean chainBuilder = true;

  private PsiAnnotation obtainViaAnnotation;
  private String viaFieldName;
  private String viaMethodName;
  private boolean viaStaticCall;

  public static BuilderInfo fromPsiParameter(@NotNull PsiParameter psiParameter) {
    final BuilderInfo result = new BuilderInfo();

    result.variableInClass = psiParameter;
    result.fieldInBuilderType = psiParameter.getType();
    result.fieldInitializer = null;
    result.hasBuilderDefaultAnnotation = false;

    result.fieldInBuilderName = psiParameter.getName();

    result.singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiParameter, Singular.class);
    result.builderElementHandler = SingularHandlerFactory.getHandlerFor(psiParameter, result.singularAnnotation);

    return result;
  }

  public static BuilderInfo fromPsiField(@NotNull PsiField psiField) {
    final BuilderInfo result = new BuilderInfo();

    result.variableInClass = psiField;
    result.fieldInBuilderType = psiField.getType();
    result.fieldInitializer = psiField.getInitializer();
    result.hasBuilderDefaultAnnotation = null == PsiAnnotationSearchUtil.findAnnotation(psiField, BUILDER_DEFAULT_ANNOTATION);

    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
    result.fieldInBuilderName = accessorsInfo.removePrefix(psiField.getName());

    result.singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, Singular.class);
    result.builderElementHandler = SingularHandlerFactory.getHandlerFor(psiField, result.singularAnnotation);

    return result;
  }

  public BuilderInfo withSubstitutor(@NotNull PsiSubstitutor builderSubstitutor) {
    fieldInBuilderType = builderSubstitutor.substitute(fieldInBuilderType);
    return this;
  }

  public BuilderInfo withFluent(boolean fluentBuilder) {
    this.fluentBuilder = fluentBuilder;
    return this;
  }

  public BuilderInfo withChain(boolean chainBuilder) {
    this.chainBuilder = chainBuilder;
    return this;
  }

  public BuilderInfo withBuilderClass(@NotNull PsiClass builderClass) {
    this.builderClass = builderClass;
    this.builderClassType = PsiClassUtil.getTypeWithGenerics(builderClass);
    return this;
  }

  public BuilderInfo withObtainVia() {
    obtainViaAnnotation = PsiAnnotationSearchUtil.findAnnotation(variableInClass, BUILDER_OBTAIN_VIA_ANNOTATION);
    if (null != obtainViaAnnotation) {
      viaFieldName = PsiAnnotationUtil.getStringAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_FIELD);
      viaMethodName = PsiAnnotationUtil.getStringAnnotationValue(obtainViaAnnotation, BUILDER_OBTAIN_VIA_METHOD);
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
      if (isInitializedFinalField && hasBuilderDefaultAnnotation) {
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

  public boolean notAlreadyExistingMethod(Collection<String> existedMethodNames) {
    return notAlreadyExistingField(existedMethodNames);
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

  public boolean isFluentBuilder() {
    return fluentBuilder;
  }

  public boolean isChainBuilder() {
    return chainBuilder;
  }

  public PsiClass getBuilderClass() {
    return builderClass;
  }

  public PsiType getBuilderType() {
    return builderClassType;
  }

  public PsiAnnotation getSingularAnnotation() {
    return singularAnnotation;
  }

  public boolean hasSingularAnnotation() {
    return null != singularAnnotation;
  }

  public boolean hasObtainVaiAnnotatation() {
    return null != obtainViaAnnotation;
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

  public Collection<PsiField> renderBuilderFields() {
    return builderElementHandler.renderBuilderFields(this);
  }

  public Collection<PsiMethod> renderBuilderMethods() {
    return builderElementHandler.renderBuilderMethod(this);
  }

  public String renderBuildPrepare() {
    return builderElementHandler.renderBuildPrepare(variableInClass, fieldInBuilderName);
  }

  public String renderBuildCall() {
    return fieldInBuilderName;
  }

  public CharSequence renderToBuilderCall() {
    final StringBuilder result = new StringBuilder();

    result.append(fieldInBuilderName);
    result.append('(');
    if (hasObtainVaiAnnotatation()) {
      if (StringUtils.isNotEmpty(viaFieldName)) {
        result.append("this.");
        result.append(viaFieldName);
      } else if (StringUtils.isNotEmpty(viaMethodName)) {

        result.append(viaStaticCall ? getPsiClass().getName() : "this");
        result.append('.');
        result.append(viaMethodName);
        result.append(viaStaticCall ? "(this)" : "()");
      } else {
        result.append("this.");
        result.append(variableInClass.getName());
      }
    } else {
      result.append("this.");
      result.append(variableInClass.getName());
    }
    result.append(')');

    return result;
  }

  private PsiClass getPsiClass() {
    return builderClass.getContainingClass();
  }

  public Optional<PsiType> getObtainViaFieldVariableType() {
    PsiVariable psiVariable = variableInClass;

    if (StringUtils.isNotEmpty(viaFieldName)) {
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

}
