package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
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

public abstract class AbstractSingularHandler implements BuilderElementHandler {

  protected final String collectionQualifiedName;

  private final boolean shouldGenerateFullBodyBlock;

  AbstractSingularHandler(String qualifiedName, boolean shouldGenerateFullBodyBlock) {
    this.collectionQualifiedName = qualifiedName;
    this.shouldGenerateFullBodyBlock = shouldGenerateFullBodyBlock;
  }

  @Override
  public void addBuilderField(@NotNull List<PsiField> fields, @NotNull PsiVariable psiVariable, @NotNull PsiClass innerClass, @NotNull AccessorsInfo accessorsInfo) {
    final String fieldName = accessorsInfo.removePrefix(psiVariable.getName());

    final PsiType fieldType = getBuilderFieldType(psiVariable.getType(), psiVariable.getProject());
    final LombokLightFieldBuilder fieldBuilder =
      new LombokLightFieldBuilder(psiVariable.getManager(), fieldName, fieldType)
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(psiVariable)
        .withContainingClass(innerClass);
    fields.add(fieldBuilder);
  }

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiFieldType, @NotNull Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiType elementType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager);

    return PsiTypeUtil.createCollectionType(psiManager, CommonClassNames.JAVA_UTIL_ARRAY_LIST, elementType);
  }

  @Override
  public void addBuilderMethod(@NotNull List<PsiMethod> methods, @NotNull PsiVariable psiVariable, @NotNull String fieldName, @NotNull PsiClass innerClass, boolean fluentBuilder, PsiType returnType, String singularName) {
    final PsiType psiFieldType = psiVariable.getType();
    final PsiManager psiManager = psiVariable.getManager();

    final LombokLightMethodBuilder oneAddMethod = new LombokLightMethodBuilder(psiManager, singularName)
      .withMethodReturnType(returnType)
      .withContainingClass(innerClass)
      .withNavigationElement(psiVariable)
      .withModifier(PsiModifier.PUBLIC);

    addOneMethodParameter(oneAddMethod, psiFieldType, singularName);
    oneAddMethod.withBody(createOneAddMethodCodeBlock(innerClass, fluentBuilder, singularName, fieldName, psiFieldType));
    methods.add(oneAddMethod);

    final LombokLightMethodBuilder allAddMethod = new LombokLightMethodBuilder(psiManager, fieldName)
      .withMethodReturnType(returnType)
      .withContainingClass(innerClass)
      .withNavigationElement(psiVariable)
      .withModifier(PsiModifier.PUBLIC);

    addAllMethodParameter(allAddMethod, psiFieldType, fieldName);
    allAddMethod.withBody(createAllAddMethodCodeBlock(innerClass, fluentBuilder, fieldName, psiFieldType));
    methods.add(allAddMethod);

    final LombokLightMethodBuilder clearMethod = new LombokLightMethodBuilder(psiManager, "clear" + StringUtil.capitalize(fieldName))
      .withMethodReturnType(returnType)
      .withContainingClass(innerClass)
      .withNavigationElement(psiVariable)
      .withModifier(PsiModifier.PUBLIC)
      .withBody(createClearMethodCodeBlock(innerClass, fluentBuilder, fieldName));

    methods.add(clearMethod);
  }

  @NotNull
  private PsiCodeBlock createClearMethodCodeBlock(@NotNull PsiClass innerClass, boolean fluentBuilder, String psiFieldName) {
    final String blockText;
    if (shouldGenerateFullBodyBlock) {
      blockText = getClearMethodBody(psiFieldName, fluentBuilder);
    } else {
      blockText = fluentBuilder ? "return this;" : "";
    }
    return PsiMethodUtil.createCodeBlockFromText(blockText, innerClass);
  }

  protected abstract String getClearMethodBody(String psiFieldName, boolean fluentBuilder);

  @NotNull
  private PsiCodeBlock createOneAddMethodCodeBlock(@NotNull PsiClass innerClass, boolean fluentBuilder, @NotNull String singularName, @NotNull String psiFieldName, PsiType psiFieldType) {
    final String blockText;
    if (shouldGenerateFullBodyBlock) {
      blockText = getOneMethodBody(singularName, psiFieldName, psiFieldType, innerClass.getManager(), fluentBuilder);
    } else {
      blockText = fluentBuilder ? "return this;" : "";
    }
    return PsiMethodUtil.createCodeBlockFromText(blockText, innerClass);
  }

  @NotNull
  private PsiCodeBlock createAllAddMethodCodeBlock(@NotNull PsiClass innerClass, boolean fluentBuilder, @NotNull String psiFieldName, @NotNull PsiType psiFieldType) {
    final String blockText;
    if (shouldGenerateFullBodyBlock) {
      blockText = getAllMethodBody(psiFieldName, psiFieldType, innerClass.getManager(), fluentBuilder);
    } else {
      blockText = fluentBuilder ? "return this;" : "";
    }
    return PsiMethodUtil.createCodeBlockFromText(blockText, innerClass);
  }

  protected abstract void addOneMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName);

  protected abstract void addAllMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName);

  protected abstract String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, @NotNull PsiType psiFieldType, PsiManager psiManager, boolean fluentBuilder);

  protected abstract String getAllMethodBody(@NotNull String singularName, @NotNull PsiType psiFieldType, PsiManager psiManager, boolean fluentBuilder);

  public String createSingularName(PsiAnnotation singularAnnotation, String psiFieldName) {
    String singularName = PsiAnnotationUtil.getStringAnnotationValue(singularAnnotation, "value");
    if (StringUtil.isEmptyOrSpaces(singularName)) {
      singularName = Singulars.autoSingularize(psiFieldName);
      if (singularName == null) {
        singularName = psiFieldName;
      }
    }
    return singularName;
  }

  public static boolean validateSingularName(PsiAnnotation singularAnnotation, String psiFieldName) {
    String singularName = PsiAnnotationUtil.getStringAnnotationValue(singularAnnotation, "value");
    if (StringUtil.isEmptyOrSpaces(singularName)) {
      singularName = Singulars.autoSingularize(psiFieldName);
      return singularName != null;
    }
    return true;
  }

  @Override
  public void appendBuildCall(@NotNull StringBuilder buildMethodParameters, @NotNull String fieldName) {
    buildMethodParameters.append(fieldName);
  }
}
