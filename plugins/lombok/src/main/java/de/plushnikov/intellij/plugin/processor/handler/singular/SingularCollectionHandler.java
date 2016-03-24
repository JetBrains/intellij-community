package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

public class SingularCollectionHandler extends AbstractSingularHandler {

  public SingularCollectionHandler(boolean shouldGenerateFullBodyBlock) {
    super(shouldGenerateFullBodyBlock);
  }

  protected void addOneMethodParameter(@NotNull String singularName, @NotNull PsiType[] psiParameterTypes, @NotNull LombokLightMethodBuilder methodBuilder) {
    if (psiParameterTypes.length == 1) {
      methodBuilder.withParameter(singularName, psiParameterTypes[0]);
    }
  }

  protected void addAllMethodParameter(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull LombokLightMethodBuilder methodBuilder) {
    final Project project = methodBuilder.getProject();

    //PsiTypeUtil.getCollectionClassType((PsiClassType) psiFieldType, project, CommonClassNames.JAVA_UTIL_COLLECTION);

    final PsiType collectionType;

    final GlobalSearchScope globalsearchscope = GlobalSearchScope.allScope(project);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass genericClass = facade.findClass(CommonClassNames.JAVA_UTIL_COLLECTION, globalsearchscope);

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.putAll(genericClass, new PsiType[]{psiFieldType});
    collectionType = JavaPsiFacade.getElementFactory(project).createType(genericClass, substitutor);

    methodBuilder.withParameter(singularName, collectionType);
  }

  protected String getClearMethodBody(String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} != null) \n this.{0}.clear();\n {1}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, @NotNull PsiType[] psiParameterTypes, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = new java.util.ArrayList<{3}>(); \n" +
        "this.{0}.add({1});{2}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, singularName, fluentBuilder ? "\nreturn this;" : "",
        psiParameterTypes[0].getCanonicalText(false));
  }

  protected String getAllMethodBody(@NotNull String singularName, @NotNull PsiType[] psiParameterTypes, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = new java.util.ArrayList<{2}>(); \n"
        + "this.{0}.addAll({0});{1}";
    return MessageFormat.format(codeBlockTemplate, singularName, fluentBuilder ? "\nreturn this;" : "",
        psiParameterTypes[0].getCanonicalText(false));
  }
}
