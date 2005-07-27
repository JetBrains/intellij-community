package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateStateListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author mike
 */
public class CreateConstructorFromCallAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorFromCallAction");

  public CreateConstructorFromCallAction(PsiConstructorCall constructorCall) {
    myConstructorCall = constructorCall;
  }

  private final PsiConstructorCall myConstructorCall;

  protected void invokeImpl(final PsiClass targetClass) {
    PsiManager psiManager = myConstructorCall.getManager();
    final Project project = psiManager.getProject();
    PsiElementFactory elementFactory = psiManager.getElementFactory();

    try {
      final PsiMethod constructor = (PsiMethod)targetClass.add(elementFactory.createConstructor());

      final PsiFile file = targetClass.getContainingFile();
      TemplateBuilder templateBuilder = new TemplateBuilder(constructor);
      CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, myConstructorCall.getArgumentList(), getTargetSubstitutor(myConstructorCall));
      CreateClassFromNewAction.setupSuperCall(targetClass, constructor, templateBuilder);

      Template template = templateBuilder.buildTemplate();

      final Editor editor = positionCursor(project, targetClass.getContainingFile(), targetClass);
      TextRange textRange = constructor.getTextRange();
      editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
      editor.getCaretModel().moveToOffset(textRange.getStartOffset());

      startTemplate(editor, template, project, new TemplateStateListener() {
        public void templateFinished(Template template) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                final int offset = editor.getCaretModel().getOffset();
                PsiMethod constructor = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethod.class, false);
                CreateFromUsageUtils.setupMethodBody(constructor);
                CreateFromUsageUtils.setupEditor(constructor, editor);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          });
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  private PsiFile getTargetFile(PsiElement element) {
    final PsiConstructorCall constructorCall = (PsiConstructorCall)element;

    //Enum constants constructors are file local
    if (constructorCall instanceof PsiEnumConstant) return constructorCall.getContainingFile();

    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(constructorCall);
    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement)referenceElement.getQualifier();
      PsiElement psiElement = qualifier.resolve();
      if (psiElement instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)psiElement;
        return psiClass.getContainingFile();
      }
    }

    return null;
  }

  protected PsiElement getElement() {
    if (!myConstructorCall.isValid() || !myConstructorCall.getManager().isInProject(myConstructorCall)) return null;

    PsiExpressionList argumentList = myConstructorCall.getArgumentList();
    if (argumentList == null) return null;

    if (myConstructorCall instanceof PsiEnumConstant) return myConstructorCall;

    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(myConstructorCall);
    if (referenceElement == null) return null;
    if (referenceElement.getReferenceNameElement() instanceof PsiIdentifier) return myConstructorCall;

    return null;
  }

  protected boolean isValidElement(PsiElement element) {
    PsiConstructorCall constructorCall = (PsiConstructorCall)element;
    PsiMethod method = constructorCall.resolveConstructor();
    PsiExpressionList argumentList = constructorCall.getArgumentList();
    PsiClass targetClass = getTargetClasses(constructorCall)[0];

    return !CreateFromUsageUtils.shouldCreateConstructor(targetClass, argumentList, method);
  }

  protected boolean isAvailableImpl(int offset) {
    PsiElement element = getElement(myConstructorCall);

    PsiFile targetFile = getTargetFile(myConstructorCall);
    if (targetFile != null && !targetFile.getManager().isInProject(targetFile)) {
      return false;
    }

    if (shouldShowTag(offset, element, myConstructorCall)) {
      setText("Create Constructor");
      return true;
    }

    return false;
  }

  private PsiJavaCodeReferenceElement getReferenceElement(PsiConstructorCall constructorCall) {
    if (constructorCall instanceof PsiNewExpression) {
      return ((PsiNewExpression)constructorCall).getClassReference();
    }

    return null;
  }

  private PsiElement getElement(PsiElement targetElement) {
    if (targetElement instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression)targetElement);
      if (referenceElement == null) return null;
      return referenceElement.getReferenceNameElement();
    } else if (targetElement instanceof PsiEnumConstant) {
      return targetElement;
    }

    return null;
  }

  public String getFamilyName() {
    return "Create Constructor from New";
  }
}
