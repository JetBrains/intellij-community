package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class CreateParameterFromUsageAction extends CreateVarFromUsageAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateParameterFromUsageAction");

  public CreateParameterFromUsageAction(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    if(!CreateFromUsageUtils.isSimpleReference(myReferenceExpression)) return false;
    Class[] scopes = new Class[] {PsiMethod.class, PsiClass.class};
    PsiElement scope = PsiTreeUtil.getParentOfType(myReferenceExpression, scopes);
    return scope instanceof PsiMethod && ((PsiMethod)scope).getParameterList().isPhysical();
  }

    public String getText(String varName) {
    return "Create Parameter '" + varName + "'";
  }

  protected void invokeImpl(PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression)) {
      return;
    }

    final PsiManager psiManager = myReferenceExpression.getManager();
    final Project project = psiManager.getProject();
    final PsiElementFactory factory = psiManager.getElementFactory();


    final PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
    PsiType type = expectedTypes[0];

    String varName = myReferenceExpression.getReferenceName();
    PsiMethod method = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiMethod.class);
    PsiParameter param;
    try {
      param = factory.createParameter(varName, type);
      param.getModifierList().setModifierProperty(PsiModifier.FINAL, CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS &&
                                                                   !PsiUtil.isAccessedForWriting(myReferenceExpression));

      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0 && parameters[parameters.length - 1].isVarArgs()) {
        param = (PsiParameter)method.getParameterList().addBefore(param, parameters[parameters.length - 1]);
      } else {
        param = (PsiParameter) method.getParameterList().add(param);
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    TemplateBuilder builder = new TemplateBuilder (method);
    builder.replaceElement(param.getTypeElement(), new TypeExpression(project, expectedTypes));
    builder.setEndVariableAfter(method.getParameterList());
    Template template = builder.buildTemplate();

    Editor editor = positionCursor(project, method.getContainingFile(), method);
    TextRange range = method.getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    TemplateManager.getInstance(project).startTemplate(editor, template);
  }

  public String getFamilyName() {
    return "Create Parameter from Usage";
  }

}
