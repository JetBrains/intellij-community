package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
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

  protected void invokeImpl(PsiClass targetClass) {
    final PsiManager psiManager = myConstructorCall.getManager();
    final Project project = psiManager.getProject();
    final PsiElementFactory elementFactory = psiManager.getElementFactory();

    try {
      PsiMethod constructor = elementFactory.createConstructor();
      constructor = (PsiMethod)targetClass.add(constructor);

      TemplateBuilder templateBuilder = new TemplateBuilder(constructor);
      CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, myConstructorCall.getArgumentList(), getTargetSubstitutor(myConstructorCall));

      CreateClassFromNewAction.setupSuperCall(targetClass, constructor, templateBuilder);

      getReferenceElement(myConstructorCall).bindToElement(targetClass);

      final Template template = templateBuilder.buildTemplate();

      final Editor editor = positionCursor(project, targetClass.getContainingFile(), targetClass);
      final TextRange textRange = constructor.getTextRange();
      editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
      editor.getCaretModel().moveToOffset(textRange.getStartOffset());

      startTemplate(editor, template, project);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  private PsiFile getTargetFile(PsiElement element) {
    final PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiConstructorCall)element);
    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement)referenceElement.getQualifier();
      final PsiElement psiElement = qualifier.resolve();
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
    final PsiJavaCodeReferenceElement referenceElement = getReferenceElement(myConstructorCall);
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
    if (!myConstructorCall.isWritable()) return false;
    final PsiElement nameElement = getElement(myConstructorCall);

    final PsiFile targetFile = getTargetFile(myConstructorCall);
    if (targetFile != null && !targetFile.isWritable()) {
      return false;
    }

    if (shouldShowTag(offset, nameElement, myConstructorCall)) {
      setText("Create Constructor");
      return true;
    }

    return false;
  }

  private PsiJavaCodeReferenceElement getReferenceElement(PsiConstructorCall constructorCall) {
    if (constructorCall instanceof PsiNewExpression) {
      return ((PsiNewExpression)constructorCall).getClassReference();
    }
    else if (constructorCall instanceof PsiEnumConstant) {
      PsiEnumConstant enumConstant = (PsiEnumConstant)constructorCall;
      PsiClassType type = (PsiClassType)enumConstant.getType();
      if (type != null) {
        return constructorCall.getManager().getElementFactory().createReferenceElementByType(type);
      }
    }

    return null;
  }

  private PsiElement getElement(PsiElement targetElement) {
    if (targetElement instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression)targetElement);
      if (referenceElement == null) return null;
      return referenceElement.getReferenceNameElement();
    } else if (targetElement instanceof PsiEnumConstant) {
      PsiEnumConstant enumConstant = (PsiEnumConstant)targetElement;
      PsiExpressionList argumentList = enumConstant.getArgumentList();
      return argumentList != null ? (PsiElement)argumentList : enumConstant;
    }

    return null;
  }

  public String getFamilyName() {
    return "Create Constructor from New";
  }
}
