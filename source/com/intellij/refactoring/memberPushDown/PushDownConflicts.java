package com.intellij.refactoring.memberPushDown;

import com.intellij.psi.*;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import java.util.*;

public class PushDownConflicts {
  private PsiClass myClass;
  private Set myMovedMembers;
  private Set myAbstractMembers;
  private ArrayList myConflicts;


  public PushDownConflicts(PsiClass aClass, MemberInfo[] memberInfos) {
    myClass = aClass;

    myMovedMembers = new HashSet();
    myAbstractMembers = new HashSet();
    for (int i = 0; i < memberInfos.length; i++) {
      MemberInfo memberInfo = memberInfos[i];
      final PsiElement member = memberInfo.getMember();
      if(memberInfo.isChecked() && (!(memberInfo.getMember() instanceof PsiClass) || memberInfo.getOverrides() == null)) {
        myMovedMembers.add(member);
        if(memberInfo.isToAbstract()) {
          myAbstractMembers.add(member);
        }
      }
    }

    myConflicts = new ArrayList();
  }

  public boolean isAnyConflicts() {
    return !myConflicts.isEmpty();
  }

  public String[] getConflicts() {
    return (String[]) myConflicts.toArray(new String[myConflicts.size()]);
  }

  public void checkSourceClassConflicts() {
    final PsiElement[] children = myClass.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if(child instanceof PsiClass || child instanceof PsiMethod || child instanceof PsiField) {
        if (!myMovedMembers.contains(child)) {
          child.accept(new UsedMovedMembersConflictsCollector(child));
        }
      }
    }
  }

  public void checkTargetClassConflicts(PsiClass targetClass) {
    for (Iterator iterator = myMovedMembers.iterator(); iterator.hasNext();) {
      PsiElement element = (PsiElement) iterator.next();
      if(element instanceof PsiField) {
        String name = ((PsiField) element).getName();
        if(targetClass.findFieldByName(name, false) != null) {
          String message = ConflictsUtil.getDescription(targetClass, false) + " already contains field "
                  + ConflictsUtil.htmlEmphasize(name) + ".";
          myConflicts.add(ConflictsUtil.capitalize(message));
        }
      }
      else if(element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) element;
        if(targetClass.findMethodBySignature(method, false) != null) {
          String message = ConflictsUtil.getDescription(method, true) + " is already overridden in "
                  + ConflictsUtil.getDescription(targetClass, false) + ". Method will not be pushed down to that class.";
          myConflicts.add(ConflictsUtil.capitalize(message));
        }
      }
      else if(element instanceof PsiClass) {
        PsiClass aClass = (PsiClass) element;
        final String name = aClass.getName();
        final PsiClass[] allInnerClasses = targetClass.getAllInnerClasses();
        for (int i = 0; i < allInnerClasses.length; i++) {
          PsiClass innerClass = allInnerClasses[i];

          if(name.equals(innerClass.getName())) {
            String message = ConflictsUtil.getDescription(targetClass, false) + " already contains inner class named "
                    + ConflictsUtil.htmlEmphasize(name) + ".";
            myConflicts.add(message);
          }
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
        String message = ConflictsUtil.getDescription(mySource, false)
                + " uses " + ConflictsUtil.getDescription(classMember, false) + ", which is pushed down";
        message = ConflictsUtil.capitalize(message);
        myConflicts.add(message);
      }
    }
  }
}
