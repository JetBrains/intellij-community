package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.IdentifierRole;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class CreateClassFromUsageAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromUsageAction");

  private final boolean myCreateInterface;
  private final PsiJavaCodeReferenceElement myRefElement;

  public CreateClassFromUsageAction(PsiJavaCodeReferenceElement refElement, boolean createInterface) {
    myRefElement = refElement;
    myCreateInterface = createInterface;
  }

  public String getText(String varName) {
    if (myCreateInterface) {
      return "Create Interface '" + varName + "'";
    }
    else {
      return "Create Class '" + varName + "'";
    }
  }

  protected void invokeImpl(PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myRefElement)) {
      return;
    }
    String superClassName = null;
    if (myRefElement.getParent().getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)myRefElement.getParent().getParent();
      if (method.getThrowsList() == myRefElement.getParent()) {
        superClassName = "java.lang.Exception";
      }
    }
    final PsiClass aClass = CreateFromUsageUtils.createClass(myRefElement, myCreateInterface, superClassName);
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          if (aClass == null) return;
          try {
            myRefElement.bindToElement(aClass);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }

          OpenFileDescriptor descriptor = new OpenFileDescriptor(myRefElement.getProject(), aClass.getContainingFile().getVirtualFile(),
            aClass.getTextOffset());
          FileEditorManager.getInstance(aClass.getProject()).openTextEditor(descriptor, true);
        }
      }
    );
  }

  protected boolean isValidElement(PsiElement element) {
    return CreateFromUsageUtils.isValidReference((PsiReference)element);
  }

  protected PsiElement getElement() {
    if (!myRefElement.isValid() || !myRefElement.getManager().isInProject(myRefElement)) return null;
    CodeStyleManager codeStyleManager = myRefElement.getManager().getCodeStyleManager();
    if (!CreateFromUsageUtils.isValidReference(myRefElement) &&
        myRefElement.getReferenceNameElement() != null &&
        codeStyleManager.checkIdentifierRole(myRefElement.getReferenceName(), IdentifierRole.CLASS_NAME)) {
      PsiElement parent = myRefElement.getParent();

      if (parent instanceof PsiTypeElement) {
        if (parent.getParent() instanceof PsiReferenceParameterList) return myRefElement;

        while (parent.getParent() instanceof PsiTypeElement) parent = parent.getParent();
        if (parent.getParent() instanceof PsiVariable || parent.getParent() instanceof PsiMethod ||
            parent.getParent() instanceof PsiClassObjectAccessExpression ||
            parent.getParent() instanceof PsiTypeCastExpression ||
            (parent.getParent() instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression)parent.getParent()).getCheckType() == parent)) {
          return myRefElement;
        }
      }
      else if (parent instanceof PsiReferenceList) {
        if (parent.getParent() instanceof PsiClass) {
          PsiClass psiClass = (PsiClass)parent.getParent();
          if (psiClass.getExtendsList() == parent) {
            if (!myCreateInterface && !psiClass.isInterface()) return myRefElement;
            if (myCreateInterface && psiClass.isInterface()) return myRefElement;
          }
          if (psiClass.getImplementsList() == parent && myCreateInterface) return myRefElement;
        }
        else if (parent.getParent() instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)parent.getParent();
          if (method.getThrowsList() == parent && !myCreateInterface) return myRefElement;
        }
      }
      else if (parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getBaseClassReference() == myRefElement) {
        return myRefElement;
      }
    }

    if (myRefElement instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)myRefElement;

      final PsiElement parent = referenceExpression.getParent();

      if (parent instanceof PsiMethodCallExpression) {
        return null;
      }
      if (parent.getParent() instanceof PsiMethodCallExpression && myCreateInterface) return null;

      if (referenceExpression.getReferenceNameElement() != null &&
          codeStyleManager.checkIdentifierRole(referenceExpression.getReferenceName(), IdentifierRole.CLASS_NAME) &&
          !CreateFromUsageUtils.isValidReference(referenceExpression)) {
        return referenceExpression;
      }
    }

    return null;
  }

  protected boolean isAvailableImpl(int offset) {
    final PsiElement nameElement = myRefElement.getReferenceNameElement();
    PsiElement parent = myRefElement.getParent();
    if (parent instanceof PsiExpression && !(parent instanceof PsiReferenceExpression)) return false;
    if (shouldShowTag(offset, nameElement, myRefElement)) {
      setText(getText(nameElement.getText()));
      return true;
    }

    return false;
  }

  public String getFamilyName() {
    return "Create Class from Usage";
  }

  public boolean startInWriteAction() {
    return false;
  }
}
