
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;

public class ChangeContextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ChangeContextUtil");

  private static final Key<String> ENCODED_KEY = Key.create("ENCODED_KEY");
  private static final Key<PsiClass> THIS_QUALIFIER_CLASS_KEY = Key.create("THIS_QUALIFIER_CLASS_KEY");
  private static final Key<PsiMember> REF_MEMBER_KEY = Key.create("REF_MEMBER_KEY");
  private static final Key<Boolean> CAN_REMOVE_QUALIFIER_KEY = Key.create("CAN_REMOVE_QUALIFIER_KEY");
  private static final Key<PsiClass> REF_CLASS_KEY = Key.create("REF_CLASS_KEY");
  private static final Key<PsiClass> REF_MEMBER_THIS_CLASS_KEY = Key.create("REF_MEMBER_THIS_CLASS_KEY");;

  public static void encodeContextInfo(PsiElement scope, boolean includeRefClasses) {
    if (scope instanceof PsiThisExpression){
      scope.putCopyableUserData(ENCODED_KEY, "");

      PsiThisExpression thisExpr = (PsiThisExpression)scope;
      if (thisExpr.getQualifier() == null){
        PsiClass thisClass = RefactoringUtil.getThisClass(thisExpr);
        if (thisClass != null && !(thisClass instanceof PsiAnonymousClass)){
          thisExpr.putCopyableUserData(THIS_QUALIFIER_CLASS_KEY, thisClass);
        }
      }
    }
    else if (scope instanceof PsiReferenceExpression){
      scope.putCopyableUserData(ENCODED_KEY, "");

      PsiReferenceExpression refExpr = (PsiReferenceExpression)scope;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier == null){
        final PsiElement refElement = refExpr.resolve();
        if (refElement != null){
          if (refElement instanceof PsiClass){
            if (includeRefClasses){
              refExpr.putCopyableUserData(REF_CLASS_KEY, ( (PsiClass)refElement));
            }
          }
          else if (refElement instanceof PsiMember){
            final PsiClass thisClass = RefactoringUtil.getThisResolveClass(refExpr);
            refExpr.putCopyableUserData(REF_MEMBER_KEY, ( (PsiMember)refElement));
            refExpr.putCopyableUserData(REF_MEMBER_THIS_CLASS_KEY, thisClass);
          }
        }
      }
      else{
        refExpr.putCopyableUserData(CAN_REMOVE_QUALIFIER_KEY, canRemoveQualifier(refExpr) ? Boolean.TRUE : Boolean.FALSE);
      }
    }
    else if (includeRefClasses) {
      PsiReference ref = scope.getReference();
      if (ref != null){
        scope.putCopyableUserData(ENCODED_KEY, "");

        PsiElement refElement = ref.resolve();
        if (refElement instanceof PsiClass){
          scope.putCopyableUserData(REF_CLASS_KEY, ( (PsiClass)refElement));
        }
      }
    }

    for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
      encodeContextInfo(child, includeRefClasses);
    }
  }

  public static PsiElement decodeContextInfo(PsiElement scope,
                                             PsiClass thisClass,
                                             PsiExpression thisAccessExpr) throws IncorrectOperationException {
    if (scope.getCopyableUserData(ENCODED_KEY) != null){
      scope.putCopyableUserData(ENCODED_KEY, null);

      if (scope instanceof PsiThisExpression){
        PsiThisExpression thisExpr = (PsiThisExpression)scope;
        scope = decodeThisExpression(thisExpr, thisClass, thisAccessExpr);
      }
      else if (scope instanceof PsiReferenceExpression){
        scope = decodeReferenceExpression((PsiReferenceExpression)scope, thisAccessExpr, thisClass);
      }
      else {
        PsiClass refClass = scope.getCopyableUserData(REF_CLASS_KEY);
        scope.putCopyableUserData(REF_CLASS_KEY, null);

        if (refClass != null && refClass.isValid()){
          PsiReference ref = scope.getReference();
          if (ref != null && refClass.getQualifiedName() != null){
            final String qualifiedName = refClass.getQualifiedName();
            if (refClass.getManager().findClass(qualifiedName, scope.getResolveScope()) != null) {
              scope = ref.bindToElement(refClass);
            }
          }
        }
      }
    }

    if (scope instanceof PsiClass){
      if (thisAccessExpr != null){
        thisAccessExpr = (PsiExpression)qualifyThis(thisAccessExpr, thisClass);
      }
    }

    for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
      child = decodeContextInfo(child, thisClass, thisAccessExpr);
    }

    return scope;
  }

  private static PsiElement decodeThisExpression(PsiThisExpression thisExpr,
                                                 PsiClass thisClass,
                                                 PsiExpression thisAccessExpr) throws IncorrectOperationException {
    if (thisExpr.getQualifier() == null){
      PsiClass qualifierClass = thisExpr.getCopyableUserData(THIS_QUALIFIER_CLASS_KEY);
      thisExpr.putCopyableUserData(THIS_QUALIFIER_CLASS_KEY, null);

      if (qualifierClass != null && qualifierClass.isValid()){
        if (qualifierClass.equals(thisClass) && thisAccessExpr != null){
          return thisExpr.replace(thisAccessExpr);
        }
      }
    }
    else{
      PsiClass qualifierClass = (PsiClass)thisExpr.getQualifier().resolve();
      if (qualifierClass != null){
        if (qualifierClass.equals(thisClass) && thisAccessExpr != null){
          return thisExpr.replace(thisAccessExpr);
        }
      }
    }
    return thisExpr;
  }

  private static PsiReferenceExpression decodeReferenceExpression(PsiReferenceExpression refExpr,
                                                                  PsiExpression thisAccessExpr,
                                                                  PsiClass thisClass) throws IncorrectOperationException {
    PsiManager manager = refExpr.getManager();
    PsiElementFactory factory = manager.getElementFactory();
    Project project = refExpr.getProject();

    PsiExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null){
      PsiElement refMember = refExpr.getCopyableUserData(REF_MEMBER_KEY);
      refExpr.putCopyableUserData(REF_MEMBER_KEY, null);

      if (refMember != null && refMember.isValid()){
        PsiClass parentClass = (PsiClass)refMember.getParent();
        boolean isStatic = ((PsiModifierListOwner) refMember).hasModifierProperty(PsiModifier.STATIC);
        if (isStatic){
          PsiElement refElement = refExpr.resolve();
          if (!manager.areElementsEquivalent(refMember, refElement)){
            PsiReferenceExpression qualifiedExpr = (PsiReferenceExpression)factory.createExpressionFromText("q." + refExpr.getText(), null);
            qualifiedExpr = (PsiReferenceExpression)CodeStyleManager.getInstance(project).reformat(qualifiedExpr);
            PsiExpression newQualifier = factory.createReferenceExpression(parentClass);
            qualifiedExpr.getQualifierExpression().replace(newQualifier);
            refExpr = (PsiReferenceExpression)refExpr.replace(qualifiedExpr);
          }
        }
        else if (thisAccessExpr != null){
          final PsiClass realParentClass = refExpr.getCopyableUserData(REF_MEMBER_THIS_CLASS_KEY);
          refExpr.putCopyableUserData(REF_MEMBER_THIS_CLASS_KEY, null);
          if (realParentClass.equals(thisClass) || (thisClass != null && thisClass.isInheritor(realParentClass, true))){
            boolean needQualifier = true;
            PsiElement refElement = refExpr.resolve();
            if (refMember.equals(refElement)){
              final PsiClass currentClass = findThisClass(refExpr, refElement);
              //needQualifier = realParentClass.equals(currentClass) ||
              //                (currentClass != null && currentClass.isInheritor(realParentClass, true));//parentClass.equals(currentClass);

              if (needQualifier && thisAccessExpr instanceof PsiThisExpression){
                PsiJavaCodeReferenceElement thisQualifier = ((PsiThisExpression)thisAccessExpr).getQualifier();
                PsiClass thisExprClass = thisQualifier != null
                  ? (PsiClass)thisQualifier.resolve()
                  : RefactoringUtil.getThisClass(refExpr);
                if (currentClass.equals(thisExprClass) || thisExprClass.isInheritor(realParentClass, true)){ // qualifier is not necessary
                  needQualifier = false;
                }
              }
            }

            if (needQualifier){
              final String text = "q." + refExpr.getText();
              final PsiExpression expressionFromText = factory.createExpressionFromText(text, null);
              LOG.assertTrue(expressionFromText instanceof PsiReferenceExpression, text);
              PsiReferenceExpression qualifiedExpr = (PsiReferenceExpression)expressionFromText;
              qualifiedExpr.getQualifierExpression().replace(thisAccessExpr);
              refExpr = (PsiReferenceExpression)refExpr.replace(qualifiedExpr);
            }
          }
        }
      }
      else {
        PsiClass refClass = refExpr.getCopyableUserData(REF_CLASS_KEY);
        refExpr.putCopyableUserData(REF_CLASS_KEY, null);
        if (refClass != null && refClass.isValid()){
          refExpr = (PsiReferenceExpression)refExpr.bindToElement(refClass);
        }
      }
    }
    else{
      Boolean couldRemove = refExpr.getCopyableUserData(CAN_REMOVE_QUALIFIER_KEY);
      refExpr.putCopyableUserData(CAN_REMOVE_QUALIFIER_KEY, null);

      if (couldRemove == Boolean.FALSE && canRemoveQualifier(refExpr)){
        PsiReferenceExpression newRefExpr = (PsiReferenceExpression)factory.createExpressionFromText(
          refExpr.getReferenceName(), null);
        refExpr = (PsiReferenceExpression)refExpr.replace(newRefExpr);
      }
    }
    return refExpr;
  }

  private static PsiClass findThisClass(PsiReferenceExpression refExpr, PsiElement refMember) {
    LOG.assertTrue(refExpr.getQualifierExpression() == null);
    if (!(refMember.getParent() instanceof PsiClass)) return null;
    final PsiClass refMemberClass = (PsiClass)refMember.getParent();
    PsiElement parent = refExpr.getContext();
    while(true){
      if (parent instanceof PsiClass){
        if (parent.equals(refMemberClass) || ((PsiClass)parent).isInheritor(refMemberClass, true)){
          return (PsiClass)parent;
        }
      }
      parent = parent.getContext();
    }
  }

  private static boolean canRemoveQualifier(PsiReferenceExpression refExpr) {
    try{
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) return false;
      PsiElement qualifierRefElement = ((PsiReferenceExpression)qualifier).resolve();
      if (!(qualifierRefElement instanceof PsiClass)) return false;
      PsiElement refElement = refExpr.resolve();
      if (refElement == null) return false;
      PsiElementFactory factory = refExpr.getManager().getElementFactory();
      if (refExpr.getParent() instanceof PsiMethodCallExpression){
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)refExpr.getParent();
        PsiMethodCallExpression newMethodCall = (PsiMethodCallExpression)factory.createExpressionFromText(
          refExpr.getReferenceName() + "()", refExpr);
        newMethodCall.getArgumentList().replace(methodCall.getArgumentList());
        PsiElement newRefElement = newMethodCall.getMethodExpression().resolve();
        return refElement.equals(newRefElement);
      }
      else{
        PsiReferenceExpression newRefExpr = (PsiReferenceExpression)factory.createExpressionFromText(
          refExpr.getReferenceName(), refExpr);
        PsiElement newRefElement = newRefExpr.resolve();
        return refElement.equals(newRefElement);
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
      return false;
    }
  }

  private static PsiElement qualifyThis(PsiElement scope, PsiClass thisClass) throws IncorrectOperationException {
    if (scope instanceof PsiThisExpression){
      PsiThisExpression thisExpr = (PsiThisExpression)scope;
      if (thisExpr.getQualifier() == null){
        if (thisClass instanceof PsiAnonymousClass) return null;
        PsiThisExpression qualifiedThis = RefactoringUtil.createThisExpression(thisClass.getManager(), thisClass);
        if (thisExpr.getParent() != null) {
          return thisExpr.replace(qualifiedThis);
        } else {
          return qualifiedThis;
        }
      }
    }
    else if (!(scope instanceof PsiClass)){
      for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
        if (qualifyThis(child, thisClass) == null) return null;
      }
    }
    return scope;
  }

  public static PsiClass getThisClass(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiClass.class);
  }

  public static void clearContextInfo(PsiElement scope) {
    scope.putCopyableUserData(THIS_QUALIFIER_CLASS_KEY, null);
    scope.putCopyableUserData(REF_MEMBER_KEY, null);
    scope.putCopyableUserData(CAN_REMOVE_QUALIFIER_KEY, null);
    for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
      clearContextInfo(child);
    }
  }
}
