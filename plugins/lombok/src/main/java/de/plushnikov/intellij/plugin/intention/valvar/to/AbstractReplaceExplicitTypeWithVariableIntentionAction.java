package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import de.plushnikov.intellij.plugin.intention.valvar.AbstractValVarIntentionAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractReplaceExplicitTypeWithVariableIntentionAction extends AbstractValVarIntentionAction {

  private final Class<?> variableClass;

  public AbstractReplaceExplicitTypeWithVariableIntentionAction(Class<?> variableClass) {
    this.variableClass = variableClass;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace explicit type with '" + variableClass.getSimpleName() + "' (Lombok)";
  }

  @Override
  public boolean isAvailableOnDeclarationStatement(PsiDeclarationStatement context) {
    PsiElement[] declaredElements = context.getDeclaredElements();
    if (declaredElements.length > 1) {
      return false;
    }
    PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiLocalVariable)) {
      return false;
    }
    PsiLocalVariable localVariable = (PsiLocalVariable) declaredElement;
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

  protected abstract boolean isAvailableOnDeclarationCustom(PsiDeclarationStatement context, PsiLocalVariable localVariable);

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
    PsiClass variablePsiClass = JavaPsiFacade.getInstance(project).findClass(variableClass.getName(), psiVariable.getResolveScope());
    if (variablePsiClass == null) {
      return;
    }
    PsiJavaCodeReferenceElement referenceElementByFQClassName = elementFactory.createReferenceElementByFQClassName(variableClass.getName(), psiVariable.getResolveScope());
    typeElement = (PsiTypeElement) IntroduceVariableBase.expandDiamondsAndReplaceExplicitTypeWithVar(typeElement, typeElement);
    typeElement.deleteChildRange(typeElement.getFirstChild(), typeElement.getLastChild());
    typeElement.add(referenceElementByFQClassName);
    RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(psiVariable);
    executeAfterReplacing(psiVariable);
    CodeStyleManager.getInstance(project).reformat(psiVariable);
  }

  protected abstract void executeAfterReplacing(PsiVariable psiVariable);
}
