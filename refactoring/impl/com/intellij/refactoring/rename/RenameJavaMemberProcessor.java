package com.intellij.refactoring.rename;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.usageView.UsageInfo;
import com.intellij.openapi.diagnostic.Logger;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class RenameJavaMemberProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameJavaMemberProcessor");

  protected static void qualifyMember(PsiMember member, PsiElement occurence, String newName) throws IncorrectOperationException {
    qualifyMember(occurence, newName, member.getContainingClass(), member.hasModifierProperty(PsiModifier.STATIC));
  }

  protected static void qualifyMember(final PsiElement occurence, final String newName, final PsiClass containingClass, final boolean isStatic)
      throws IncorrectOperationException {
    PsiManager psiManager = occurence.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    if (isStatic) {
      PsiReferenceExpression qualified = (PsiReferenceExpression)factory.createExpressionFromText("a." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      qualified.getQualifierExpression().replace(factory.createReferenceExpression(containingClass));
      occurence.replace(qualified);
    }
    else {
      PsiReferenceExpression qualified = createQualifiedMemberReference(occurence, newName, containingClass, isStatic);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      occurence.replace(qualified);
    }
  }

  public static PsiReferenceExpression createMemberReference(PsiMember member, PsiElement context) throws IncorrectOperationException {
    final PsiManager manager = member.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final String name = member.getName();
    PsiReferenceExpression ref = (PsiReferenceExpression) factory.createExpressionFromText(name, context);
    PsiElement resolved = ref.resolve();
    if (manager.areElementsEquivalent(resolved, member)) return ref;
    return createQualifiedMemberReference(context, name, member.getContainingClass(), member.hasModifierProperty(PsiModifier.STATIC));
  }

  protected static PsiReferenceExpression createQualifiedMemberReference(final PsiElement context, final String name,
                                                                         final PsiClass containingClass, final boolean isStatic) throws IncorrectOperationException {
    PsiReferenceExpression ref;
    final PsiJavaCodeReferenceElement qualifier;

    final PsiManager manager = containingClass.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (isStatic) {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("A." + name, context);
      qualifier = (PsiReferenceExpression)ref.getQualifierExpression();
      final PsiReferenceExpression classReference = factory.createReferenceExpression(containingClass);
      qualifier.replace(classReference);
    }
    else {
      PsiClass contextClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
      if (InheritanceUtil.isInheritorOrSelf(contextClass, containingClass, true)) {
        ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + name, context);
        return ref;
      }
      ref = (PsiReferenceExpression) factory.createExpressionFromText("A.this." + name, null);
      qualifier = ((PsiThisExpression)ref.getQualifierExpression()).getQualifier();
      final PsiJavaCodeReferenceElement classReference = factory.createClassReferenceElement(containingClass);
      qualifier.replace(classReference);
    }
    return ref;
  }

  protected static void findMemberHidesOuterMemberCollisions(final PsiMember member, final String newName, final List<UsageInfo> result) {
    final PsiMember patternMember;
    if (member instanceof PsiMethod) {
      PsiMethod patternMethod = (PsiMethod) member.copy();
      try {
        patternMethod.setName(newName);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return;
      }
      patternMember = patternMethod;
    }
    else {
      patternMember = member;
    }

    final PsiClass fieldClass = member.getContainingClass();
    for (PsiClass aClass = fieldClass.getContainingClass(); aClass != null; aClass = aClass.getContainingClass()) {
      final PsiMember conflict;
      if (member instanceof PsiMethod) {
        conflict = aClass.findMethodBySignature((PsiMethod)patternMember, true);
      }
      else {
        conflict = aClass.findFieldByName(newName, false);
      }
      if (conflict == null) continue;
      ReferencesSearch.search(conflict).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference reference) {
          PsiElement refElement = reference.getElement();
          if (refElement instanceof PsiReferenceExpression && ((PsiReferenceExpression)refElement).isQualified()) return true;
          if (PsiTreeUtil.isAncestor(fieldClass, refElement, false)) {
            MemberHidesOuterMemberUsageInfo info = new MemberHidesOuterMemberUsageInfo(refElement, member);
            result.add(info);
          }
          return true;
        }
      });
    }
  }

  protected static void qualifyOuterMemberReferences(final List<MemberHidesOuterMemberUsageInfo> outerHides) throws IncorrectOperationException {
    for (MemberHidesOuterMemberUsageInfo usage : outerHides) {
      final PsiElement element = usage.getElement();
      PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
      PsiMember member = (PsiMember)usage.getReferencedElement();
      PsiReferenceExpression ref = createMemberReference(member, collidingRef);
      collidingRef.replace(ref);
    }
  }

  protected void findCollisionsAgainstNewName(final PsiMember memberToRename, final String newName, final List<UsageInfo> result) {
    final List<PsiReference> potentialConflicts = new ArrayList<PsiReference>();
    PsiMember prototype = (PsiMember)memberToRename.copy();
    try {
      ((PsiNamedElement) prototype).setName(newName);
      prototype = (PsiMember) memberToRename.getContainingClass().add(prototype);

      ReferencesSearch.search(prototype).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference psiReference) {
          potentialConflicts.add(psiReference);
          return true;
        }
      });

      prototype.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    for (PsiReference potentialConflict : potentialConflicts) {
      if (potentialConflict instanceof PsiJavaReference) {
        final JavaResolveResult resolveResult = ((PsiJavaReference)potentialConflict).advancedResolve(false);
        final PsiElement conflictElement = resolveResult.getElement();
        if (conflictElement != null) {
          final PsiElement scope = resolveResult.getCurrentFileResolveScope();
          if (scope instanceof PsiImportStaticStatement) {
            result.add(new MemberHidesStaticImportUsageInfo(potentialConflict.getElement(), conflictElement, memberToRename));
          }
        }
      }
    }
  }

  protected void qualifyStaticImportReferences(final List<MemberHidesStaticImportUsageInfo> staticImportHides)
      throws IncorrectOperationException {
    for (MemberHidesStaticImportUsageInfo info : staticImportHides) {
      final PsiReference ref = info.getReference();
      if (ref == null) return;
      final PsiElement occurrence = ref.getElement();
      final PsiElement target = info.getReferencedElement();
      if (target instanceof PsiMember && occurrence != null) {
        final PsiMember targetMember = (PsiMember)target;
        PsiClass containingClass = targetMember.getContainingClass();
        qualifyMember(occurrence, targetMember.getName(), containingClass, true);
      }
    }
  }
}
