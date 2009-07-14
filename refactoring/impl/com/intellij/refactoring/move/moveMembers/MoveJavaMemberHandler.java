package com.intellij.refactoring.move.moveMembers;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */

public class MoveJavaMemberHandler implements MoveMemberHandler {
  public MoveMembersProcessor.MoveMembersUsageInfo getUsage(PsiMember member,
                                                            PsiReference psiReference,
                                                            Set<PsiMember> membersToMove,
                                                            PsiClass targetClass) {
    PsiElement ref = psiReference.getElement();
    if (ref instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (RefactoringHierarchyUtil.willBeInTargetClass(refExpr, membersToMove, targetClass, true)) {
        // both member and the reference to it will be in target class
        if (!isInMovedElement(refExpr, membersToMove)) {
          if (qualifier != null) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // remove qualifier
          }
        }
        else {
          if (qualifier instanceof PsiReferenceExpression &&
              ((PsiReferenceExpression)qualifier).isReferenceTo(member.getContainingClass())) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // change qualifier
          }
        }
      }
      else {
        // member in target class, the reference will be outside target class
        if (qualifier == null) {
          return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, targetClass, refExpr, psiReference); // add qualifier
        }
        else {
          return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, targetClass, qualifier, psiReference); // change qualifier
        }
      }
    }
    return null;
  }

  private boolean isInMovedElement(PsiElement element, Set<PsiMember> membersToMove) {
    for (PsiMember member : membersToMove) {
      if (PsiTreeUtil.isAncestor(member, element, false)) return true;
    }
    return false;
  }

  public boolean changeExternalUsage(MoveMembersOptions options, MoveMembersProcessor.MoveMembersUsageInfo usage) {
    if (!usage.getElement().isValid()) return true;

    if (usage.reference instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)usage.reference;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        if (usage.qualifierClass != null) {
          changeQualifier(refExpr, usage.qualifierClass);
        }
        else {
          refExpr.setQualifierExpression(null);
        }
      }
      else { // no qualifier
        if (usage.qualifierClass != null) {
          changeQualifier(refExpr, usage.qualifierClass);
        }
      }
      return true;
    }
    return false;
  }

  public PsiMember doMove(MoveMembersOptions options, PsiMember member, ArrayList<MoveMembersProcessor.MoveMembersUsageInfo> otherUsages) {
    if (member instanceof PsiVariable) {
      ((PsiVariable)member).normalizeDeclaration();
    }
    PsiClass targetClass = JavaPsiFacade.getInstance(member.getManager().getProject())
      .findClass(options.getTargetClassName(), GlobalSearchScope.projectScope(member.getProject()));
    ChangeContextUtil.encodeContextInfo(member, true);
    if (targetClass == null) return null;

    PsiElement anchor = getAnchor(member, targetClass);

    final PsiMember memberCopy;
    if (options.makeEnumConstant() &&
        member instanceof PsiVariable &&
        EnumConstantsUtil.isSuitableForEnumConstant(((PsiVariable)member).getType(), targetClass)) {
      memberCopy = EnumConstantsUtil.createEnumConstant(targetClass, member.getName(), ((PsiVariable)member).getInitializer());
    }
    else {
      memberCopy = (PsiMember)member.copy();
      if (member.getContainingClass().isInterface() && !targetClass.isInterface()) {
        //might need to make modifiers explicit, see IDEADEV-11416
        final PsiModifierList list = memberCopy.getModifierList();
        assert list != null;
        list.setModifierProperty(PsiModifier.STATIC, member.hasModifierProperty(PsiModifier.STATIC));
        list.setModifierProperty(PsiModifier.FINAL, member.hasModifierProperty(PsiModifier.FINAL));
        RefactoringUtil.setVisibility(list, VisibilityUtil.getVisibilityModifier(member.getModifierList()));
      }
    }
    member.delete();
    return anchor != null ? (PsiMember)targetClass.addAfter(memberCopy, anchor) : (PsiMember)targetClass.add(memberCopy);
  }

  public void decodeContextInfo(PsiElement scope) {
    ChangeContextUtil.decodeContextInfo(scope, null, null);
  }

  private void changeQualifier(PsiReferenceExpression refExpr, PsiClass aClass) throws IncorrectOperationException {
    if (RefactoringUtil.hasOnDemandStaticImport(refExpr, aClass)) {
      refExpr.setQualifierExpression(null);
    }
    else {
      PsiElementFactory factory = JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory();
      refExpr.setQualifierExpression(factory.createReferenceExpression(aClass));
    }
  }

  @Nullable
  private static PsiElement getAnchor(final PsiMember member, final PsiClass targetClass) {
    if (member instanceof PsiField && member.hasModifierProperty(PsiModifier.STATIC)) {
      final List<PsiField> referencedFields = new ArrayList<PsiField>();
      final PsiExpression psiExpression = ((PsiField)member).getInitializer();
      if (psiExpression != null) {
        psiExpression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(final PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement psiElement = expression.resolve();
            if (psiElement instanceof PsiField) {
              final PsiField psiField = (PsiField)psiElement;
              if (psiField.getContainingClass() == targetClass && !referencedFields.contains(psiField)) {
                referencedFields.add(psiField);
              }
            }
          }
        });
      }
      if (!referencedFields.isEmpty()) {
        Collections.sort(referencedFields, new Comparator<PsiField>() {
          public int compare(final PsiField o1, final PsiField o2) {
            return -PsiUtilBase.compareElementsByPosition(o1, o2);
          }
        });
        return referencedFields.get(0);
      }
    }
    return null;
  }
}
