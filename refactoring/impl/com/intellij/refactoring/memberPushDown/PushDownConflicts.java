package com.intellij.refactoring.memberPushDown;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class PushDownConflicts {
  private PsiClass myClass;
  private Set<PsiMember> myMovedMembers;
  private Set<PsiMember> myAbstractMembers;
  private ArrayList<String> myConflicts;


  public PushDownConflicts(PsiClass aClass, MemberInfo[] memberInfos) {
    myClass = aClass;

    myMovedMembers = new HashSet<PsiMember>();
    myAbstractMembers = new HashSet<PsiMember>();
    for (MemberInfo memberInfo : memberInfos) {
      final PsiMember member = memberInfo.getMember();
      if (memberInfo.isChecked() && (!(memberInfo.getMember() instanceof PsiClass) || memberInfo.getOverrides() == null)) {
        myMovedMembers.add(member);
        if (memberInfo.isToAbstract()) {
          myAbstractMembers.add(member);
        }
      }
    }

    myConflicts = new ArrayList<String>();
  }

  public boolean isAnyConflicts() {
    return !myConflicts.isEmpty();
  }

  public ArrayList<String> getConflicts() {
    return myConflicts;
  }

  public void checkSourceClassConflicts() {
    final PsiElement[] children = myClass.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiMember && !myMovedMembers.contains(child)) {
        child.accept(new UsedMovedMembersConflictsCollector(child));
      }
    }
  }

  public void checkTargetClassConflicts(PsiClass targetClass) {
    for (final PsiMember movedMember : myMovedMembers) {
      checkMemberPlacementInTargetClassConflict(targetClass, movedMember);
    }
    Members:
    for (PsiMember member : myMovedMembers) {
      for (PsiReference ref : ReferencesSearch.search(member, member.getResolveScope(), false)) {
        final PsiElement element = ref.getElement();
        if (element instanceof PsiReferenceExpression) {
          final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
          final PsiExpression qualifier = referenceExpression.getQualifierExpression();
          if (qualifier != null) {
            final PsiType qualifierType = qualifier.getType();
            if (qualifierType instanceof PsiClassType) {
              final PsiClass aClass = ((PsiClassType)qualifierType).resolve();
              if (!InheritanceUtil.isInheritorOrSelf(aClass, targetClass, true)) {
                myConflicts.add(RefactoringBundle.message("pushed.members.will.not.be.visible.from.certain.call.sites"));
                break Members;
              }
            }
          }
        }
      }
    }
  }

  public void checkMemberPlacementInTargetClassConflict(final PsiClass targetClass, final PsiMember movedMember) {
    if (movedMember instanceof PsiField) {
      String name = movedMember.getName();
      if (targetClass.findFieldByName(name, false) != null) {
        String message = RefactoringBundle.message("0.already.contains.field.1", RefactoringUIUtil.getDescription(targetClass, false), CommonRefactoringUtil.htmlEmphasize(name));
        myConflicts.add(ConflictsUtil.capitalize(message));
      }
    }
    else if (movedMember instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)movedMember;
      if (targetClass.findMethodBySignature(method, false) != null) {
        String message = RefactoringBundle.message("0.is.already.overridden.in.1",
                                              RefactoringUIUtil.getDescription(method, true), RefactoringUIUtil.getDescription(targetClass, false));
        myConflicts.add(ConflictsUtil.capitalize(message));
      }
    }
    else if (movedMember instanceof PsiClass) {
      PsiClass aClass = (PsiClass)movedMember;
      final String name = aClass.getName();
      final PsiClass[] allInnerClasses = targetClass.getAllInnerClasses();
      for (PsiClass innerClass : allInnerClasses) {
        if (innerClass.equals(movedMember)) continue;

        if (name.equals(innerClass.getName())) {
          String message = RefactoringBundle.message("0.already.contains.inner.class.named.1", RefactoringUIUtil.getDescription(targetClass, false),
                                                CommonRefactoringUtil.htmlEmphasize(name));
          myConflicts.add(message);
        }
      }
    }
  }

  private class UsedMovedMembersConflictsCollector extends ClassMemberReferencesVisitor {
    private final PsiElement mySource;

    public UsedMovedMembersConflictsCollector(PsiElement source) {
      super(myClass);
      mySource = source;
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if(myMovedMembers.contains(classMember) && !myAbstractMembers.contains(classMember)) {
        String message = RefactoringBundle.message("0.uses.1.which.is.pushed.down", RefactoringUIUtil.getDescription(mySource, false),
                                              RefactoringUIUtil.getDescription(classMember, false));
        message = ConflictsUtil.capitalize(message);
        myConflicts.add(message);
      }
    }
  }
}
