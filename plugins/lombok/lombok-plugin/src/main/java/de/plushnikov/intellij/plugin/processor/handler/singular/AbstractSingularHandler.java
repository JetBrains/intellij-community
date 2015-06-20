package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import lombok.core.handlers.Singulars;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbstractSingularHandler {

  public void addBuilderField(@NotNull List<PsiField> fields, @NotNull PsiVariable psiVariable, @NotNull PsiClass innerClass, @NotNull AccessorsInfo accessorsInfo) {
    final String fieldName = accessorsInfo.removePrefix(psiVariable.getName());
    final LombokLightFieldBuilder fieldBuilder =
        new LombokLightFieldBuilder(psiVariable.getManager(), fieldName, getBuilderFieldType(psiVariable.getType(), psiVariable.getProject()))
            .withModifier(PsiModifier.PRIVATE)
            .withNavigationElement(psiVariable)
            .withContainingClass(innerClass);
    fields.add(fieldBuilder);
  }

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiType, @NotNull Project project) {
    return PsiTypeUtil.getCollectionClassType((PsiClassType) psiType, project, CommonClassNames.JAVA_UTIL_ARRAY_LIST);
  }

  //    final PsiClass psiFieldClass = PsiUtil.resolveClassInType(psiFieldType);
//    final PsiClassType.ClassResolveResult classInType = PsiUtil.resolveGenericsClassInType(psiFieldType);
//    final String fieldFQN = psiFieldClass.getQualifiedName();

  public void addBuilderMethod(@NotNull List<PsiMethod> methods, @NotNull PsiField psiField, @NotNull PsiClass innerClass, boolean fluentBuilder, PsiType returnType, PsiAnnotation singularAnnotation) {
    final String psiFieldName = psiField.getName();
    final String singularName = createSingularName(singularAnnotation, psiFieldName);

    final PsiType psiFieldType = psiField.getType();

    final PsiManager psiManager = psiField.getManager();

    final LombokLightMethodBuilder oneAddMethod = new LombokLightMethodBuilder(psiManager, singularName)
        .withMethodReturnType(returnType)
        .withContainingClass(innerClass)
        .withNavigationElement(psiField)
        .withModifier(PsiModifier.PUBLIC)
        .withBody(PsiMethodUtil.createCodeBlockFromText(getOneMethodBody(singularName, fluentBuilder), innerClass));

    addOneMethodParameter(singularName, psiFieldType, oneAddMethod);
    methods.add(oneAddMethod);

    final LombokLightMethodBuilder allAddMethod = new LombokLightMethodBuilder(psiManager, psiFieldName)
        .withMethodReturnType(returnType)
        .withContainingClass(innerClass)
        .withNavigationElement(psiField)
        .withModifier(PsiModifier.PUBLIC)
        .withBody(PsiMethodUtil.createCodeBlockFromText(getAllMethodBody(psiFieldName, fluentBuilder), innerClass));

    addAllMethodParameter(singularName, psiFieldType, allAddMethod);
    methods.add(allAddMethod);
  }

  protected void addOneMethodParameter(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull LombokLightMethodBuilder methodBuilder) {

  }

  protected void addAllMethodParameter(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull LombokLightMethodBuilder methodBuilder) {

  }

  protected String getOneMethodBody(String singularName, boolean fluentBuilder) {
    return "";
  }

  protected String getAllMethodBody(String singularName, boolean fluentBuilder) {
    return "";
  }

  protected String createSingularName(PsiAnnotation singularAnnotation, String psiFieldName) {
    String singularName = PsiAnnotationUtil.getStringAnnotationValue(singularAnnotation, "value");
    if (StringUtil.isEmptyOrSpaces(singularName)) {
      //TODO remove accessors prefix
      singularName = Singulars.autoSingularize(psiFieldName);
      if (singularName == null) {
        //TODO addError("Can't singularize this name; please specify the singular explicitly (i.e. @Singular(\"sheep\"))");
        singularName = psiFieldName;
      }
    }
    return singularName;
  }
}
