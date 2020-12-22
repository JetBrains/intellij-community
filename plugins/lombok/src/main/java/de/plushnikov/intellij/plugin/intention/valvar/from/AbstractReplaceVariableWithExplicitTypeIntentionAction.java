package de.plushnikov.intellij.plugin.intention.valvar.from;

import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.plugin.intention.valvar.AbstractValVarIntentionAction;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractReplaceVariableWithExplicitTypeIntentionAction extends AbstractValVarIntentionAction {

  private final String variableClassName;
  private final String variableClassSimpleName;

  public AbstractReplaceVariableWithExplicitTypeIntentionAction(String variableClassName, String variableClassSimpleName) {
    this.variableClassName = variableClassName;
    this.variableClassSimpleName = variableClassSimpleName;
  }

  public AbstractReplaceVariableWithExplicitTypeIntentionAction(Class<?> variableClass) {
    this(variableClass.getName(), variableClass.getSimpleName());
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace '" + variableClassSimpleName + "' with explicit type (Lombok)";
  }

  @Override
  public boolean isAvailableOnVariable(PsiVariable psiVariable) {
    if (variableClassName == "lombok.val") {
      return ValProcessor.isVal(psiVariable);
    }
    if (variableClassName == "lombok.var") {
      return ValProcessor.isVar(psiVariable);
    }
    return false;
  }

  @Override
  public boolean isAvailableOnDeclarationStatement(PsiDeclarationStatement context) {
    if (context.getDeclaredElements().length <= 0) {
      return false;
    }
    PsiElement declaredElement = context.getDeclaredElements()[0];
    if (!(declaredElement instanceof PsiLocalVariable)) {
      return false;
    }
    return isAvailableOnVariable((PsiLocalVariable) declaredElement);
  }

  @Override
  public void invokeOnDeclarationStatement(PsiDeclarationStatement declarationStatement) {
    if (declarationStatement.getDeclaredElements().length > 0) {
      PsiElement declaredElement = declarationStatement.getDeclaredElements()[0];
      if (declaredElement instanceof PsiLocalVariable) {
        invokeOnVariable((PsiLocalVariable) declaredElement);
      }
    }
  }

  @Override
  public void invokeOnVariable(PsiVariable psiVariable) {
    PsiTypeElement psiTypeElement = psiVariable.getTypeElement();
    if (psiTypeElement == null) {
      return;
    }
    PsiTypesUtil.replaceWithExplicitType(psiTypeElement);
    RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(psiVariable);
    executeAfterReplacing(psiVariable);
    CodeStyleManager.getInstance(psiVariable.getProject()).reformat(psiVariable);
  }

  protected abstract void executeAfterReplacing(PsiVariable psiVariable);
}
