package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceVariableUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.intention.valvar.AbstractValVarIntentionAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractReplaceExplicitTypeWithVariableIntentionAction extends AbstractValVarIntentionAction {

  private final String variableClassName;

  public AbstractReplaceExplicitTypeWithVariableIntentionAction(String variableClassName) {
    this.variableClassName = variableClassName;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return LombokBundle.message("replace.explicit.type.with.0.lombok", StringUtil.getShortName(variableClassName));
  }

  @Override
  public boolean isAvailableOnDeclarationStatement(PsiDeclarationStatement context) {
    if (PsiUtil.isAvailable(JavaFeature.LVTI, context)) {
      return false;
    }
    PsiElement[] declaredElements = context.getDeclaredElements();
    if (declaredElements.length != 1) {
      return false;
    }
    PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiLocalVariable localVariable)) {
      return false;
    }
    if (!localVariable.hasInitializer()) {
      return false;
    }
    PsiExpression initializer = localVariable.getInitializer();
    if (initializer instanceof PsiArrayInitializerExpression || initializer instanceof PsiLambdaExpression) {
      return false;
    }
    if (localVariable.getTypeElement().isInferredType()) {
      return false;
    }
    return isAvailableOnDeclarationCustom(context, localVariable);
  }

  protected abstract boolean isAvailableOnDeclarationCustom(@NotNull PsiDeclarationStatement context,@NotNull PsiLocalVariable localVariable);

  @Override
  public void invokeOnDeclarationStatement(PsiDeclarationStatement declarationStatement) {
    if (declarationStatement.getDeclaredElements().length == 1) {
      PsiLocalVariable localVariable = (PsiLocalVariable) declarationStatement.getDeclaredElements()[0];
      invokeOnVariable(localVariable);
    }
  }

  @Override
  public void invokeOnVariable(PsiVariable psiVariable) {
    Project project = psiVariable.getProject();
    psiVariable.normalizeDeclaration();
    PsiTypeElement typeElement = psiVariable.getTypeElement();
    if (typeElement == null || typeElement.isInferredType()) {
      return;
    }

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiClass variablePsiClass = JavaPsiFacade.getInstance(project).findClass(variableClassName, psiVariable.getResolveScope());
    if (variablePsiClass == null) {
      return;
    }
    PsiJavaCodeReferenceElement referenceElementByFQClassName = elementFactory.createReferenceElementByFQClassName(variableClassName, psiVariable.getResolveScope());
    typeElement = (PsiTypeElement) IntroduceVariableUtil.expandDiamondsAndReplaceExplicitTypeWithVar(typeElement, typeElement);
    typeElement.deleteChildRange(typeElement.getFirstChild(), typeElement.getLastChild());
    typeElement.add(referenceElementByFQClassName);
    RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(psiVariable);
    executeAfterReplacing(psiVariable);
    CodeStyleManager.getInstance(project).reformat(psiVariable);
  }

  protected abstract void executeAfterReplacing(PsiVariable psiVariable);
}
