package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateStateListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class CreateMethodFromUsageAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageAction");

  private final PsiMethodCallExpression myMethodCall;

  public CreateMethodFromUsageAction(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  protected boolean isAvailableImpl(int offset) {
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();
    String name = ref.getReferenceName();

    if (name == null || !ref.getManager().getNameHelper().isIdentifier(name)) return false;

    setText("Create Method '" + name + "'");
    return true;
  }

  protected PsiElement getElement() {
    if (!myMethodCall.isValid() || !myMethodCall.getManager().isInProject(myMethodCall)) return null;
    return myMethodCall;
  }

  protected void invokeImpl(PsiClass targetClass) {
    PsiManager psiManager = myMethodCall.getManager();
    final Project project = psiManager.getProject();
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();

    if (isValidElement(myMethodCall)) {
      return;
    }

    PsiClass parentClass = PsiTreeUtil.getParentOfType(myMethodCall, PsiClass.class);
    PsiMember enclosingContext = PsiTreeUtil.getParentOfType(myMethodCall,
      PsiMethod.class,
      PsiField.class,
      PsiClassInitializer.class);

    if (targetClass == null) {
      return;
    }

    PsiFile targetFile = targetClass.getContainingFile();

    String methodName = ref.getReferenceName();

    try {
      PsiElementFactory factory = psiManager.getElementFactory();

      ExpectedTypeInfo[] expectedTypes = CreateFromUsageUtils.guessExpectedTypes(myMethodCall, true);

      PsiMethod method = factory.createMethod(methodName, PsiType.VOID);

      if (targetClass.equals(parentClass)) {
        method = (PsiMethod) targetClass.addAfter(method, enclosingContext);
      } else {
        PsiElement anchor = enclosingContext;

        while (anchor != null && anchor.getParent() != null && !anchor.getParent().equals(targetClass)) {
          anchor = anchor.getParent();
        }

        if (anchor != null && anchor.getParent() == null) anchor = null;

        if (anchor != null) {
          method = (PsiMethod) targetClass.addAfter(method, anchor);
        } else {
          method = (PsiMethod) targetClass.add(method);
        }
      }

      TemplateBuilder builder = new TemplateBuilder(method);

      setupVisibility(parentClass, targetClass, method.getModifierList());

      if (shouldCreateStaticMember(myMethodCall.getMethodExpression(), enclosingContext, targetClass) && !targetClass.isInterface()) {
        method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
      }

      PsiSubstitutor substitutor = getTargetSubstitutor(myMethodCall);

      CreateFromUsageUtils.setupMethodParameters(method, builder, myMethodCall.getArgumentList(), substitutor);

      PsiElement context = PsiTreeUtil.getParentOfType(myMethodCall, PsiClass.class, PsiMethod.class);
      new GuessTypeParameters(factory).setupTypeElement(method.getReturnTypeElement(), expectedTypes, substitutor, builder, context, targetClass);
      if (!targetClass.isInterface()) {
        builder.setEndVariableAfter(method.getBody().getLBrace());
      } else {
        method.getBody().delete();
        builder.setEndVariableAfter(method);
      }

      Template template = builder.buildTemplate();

      final Editor newEditor = positionCursor(project, targetFile, method);
      TextRange range = method.getTextRange();
      newEditor.getCaretModel().moveToOffset(range.getStartOffset());
      newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

      final PsiFile file = method.getContainingFile();

      if (!targetClass.isInterface()) {
        startTemplate(newEditor, template, project, new TemplateStateListener() {
          public void templateFinished(Template template) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
                final int offset = newEditor.getCaretModel().getOffset();
                PsiMethod method = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethod.class, false);

                if (method != null) {
                  try {
                    CreateFromUsageUtils.setupMethodBody(method);
                  } catch (IncorrectOperationException e) {
                  }

                  CreateFromUsageUtils.setupEditor(method, newEditor);
                }
              }
            });
          }
        });
      } else {
        startTemplate(newEditor, template, project);
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected boolean isValidElement(PsiElement element) {
    PsiMethodCallExpression callExpression = (PsiMethodCallExpression) element;
    PsiReferenceExpression referenceExpression = callExpression.getMethodExpression();

    return CreateFromUsageUtils.isValidMethodReference(referenceExpression, callExpression);
  }

  public String getFamilyName() {
    return "Create Method from Usage";
  }
}
