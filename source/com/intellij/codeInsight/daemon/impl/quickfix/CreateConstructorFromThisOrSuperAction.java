package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateStateListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: ven
 * Date: May 12, 2003
 * Time: 6:41:19 PM
 * To change this template use Options | File Templates.
 */
public abstract class CreateConstructorFromThisOrSuperAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorFromThisOrSuperAction");

  protected PsiMethodCallExpression myMethodCall;

  public CreateConstructorFromThisOrSuperAction(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  abstract protected String getSyntheticMethodName ();

  protected boolean isAvailableImpl(int offset) {
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();
    if (!ref.getText().equals(getSyntheticMethodName())) return false;

    PsiMethod method = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
    if (method == null || !method.isConstructor()) return false;

    PsiClass[] targetClasses = getTargetClasses(myMethodCall);
    LOG.assertTrue(targetClasses.length == 1);

    if (shouldShowTag(offset, ref.getReferenceNameElement(), myMethodCall)) {
      setText("Create Constructor In '" + targetClasses[0].getName() + "'");
      return true;
    }

    return false;
  }

  protected void invokeImpl(PsiClass targetClass) {
    final PsiManager psiManager = myMethodCall.getManager();
    final PsiFile callSite = myMethodCall.getContainingFile();
    final Project project = psiManager.getProject();
    final PsiElementFactory elementFactory = psiManager.getElementFactory();

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    try {
      PsiMethod constructor = elementFactory.createConstructor();
      constructor = (PsiMethod) targetClass.add(constructor);

      TemplateBuilder templateBuilder = new TemplateBuilder(constructor);
      CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, myMethodCall.getArgumentList(), getTargetSubstitutor(myMethodCall));
      templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());

      final Template template = templateBuilder.buildTemplate();
      final Editor editor = positionCursor(project, targetClass.getContainingFile(), targetClass);
      final TextRange textRange = constructor.getTextRange();
      final PsiFile file = targetClass.getContainingFile();
      editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
      editor.getCaretModel().moveToOffset(textRange.getStartOffset());

      startTemplate(editor, template, project, new TemplateStateListener() {
        public void templateFinished(Template template) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
                PsiMethod constructor = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                PsiClass targetClass = PsiTreeUtil.getParentOfType(constructor, PsiClass.class);
                CreateFromUsageUtils.setupMethodBody(targetClass, constructor);
                CreateFromUsageUtils.setupEditor(constructor, editor);

                QuickFixAction.spoilDocument(project, callSite);
              } catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          });
        }
      });
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected boolean isValidElement(PsiElement element) {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element;
    PsiMethod method = (PsiMethod) methodCall.getMethodExpression().resolve();
    PsiExpressionList argumentList = methodCall.getArgumentList();
    PsiClass targetClass = getTargetClasses(element)[0];

    return !CreateFromUsageUtils.shouldCreateConstructor(targetClass, argumentList, method);
  }

  protected PsiElement getElement() {
    if (!myMethodCall.isValid() || !myMethodCall.getManager().isInProject(myMethodCall)) return null;
    return myMethodCall;
  }
}
