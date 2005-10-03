package com.intellij.refactoring.util.classMembers;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class UsedByMemberDependencyGraph implements MemberDependencyGraph {
  protected HashSet<PsiMember> mySelectedNormal;
  protected HashSet<PsiMember> mySelectedAbstract;
  protected HashSet<PsiMember> myMembers;
  protected HashSet<PsiMember> myDependencies = null;
  protected HashMap<PsiMember,HashSet<PsiMember>> myDependenciesToDependent = null;
  private final MemberDependenciesStorage myMemberDependenciesStorage;

  UsedByMemberDependencyGraph(PsiClass aClass) {
    myMemberDependenciesStorage = new MemberDependenciesStorage(aClass, null);
    mySelectedNormal = new HashSet<PsiMember>();
    mySelectedAbstract = new HashSet<PsiMember>();
    myMembers = new HashSet<PsiMember>();
  }

  public void memberChanged(MemberInfo memberInfo) {
    if(ClassMembersUtil.isProperMember(memberInfo))
    {
      myDependencies = null;
      myDependenciesToDependent = null;
      PsiMember member = memberInfo.getMember();
      myMembers.add(member);
      if (!memberInfo.isChecked()) {
        mySelectedNormal.remove(member);
        mySelectedAbstract.remove(member);
      }
      else {
        if (memberInfo.isToAbstract()) {
          mySelectedNormal.remove(member);
          mySelectedAbstract.add(member);
        }
        else {
          mySelectedNormal.add(member);
          mySelectedAbstract.remove(member);
        }
      }
    }
  }

  public Set<? extends PsiMember> getDependent() {
    if(myDependencies == null) {
      myDependencies = new HashSet<PsiMember>();
      myDependenciesToDependent = new HashMap<PsiMember, HashSet<PsiMember>>();
      for (PsiMember member : myMembers) {
        Set<PsiMember> dependent = myMemberDependenciesStorage.getMemberDependencies(member);
        for (final PsiMember aDependent : dependent) {
          if (mySelectedNormal.contains(aDependent) && !mySelectedAbstract.contains(aDependent)) {
            myDependencies.add(member);
            HashSet<PsiMember> deps = myDependenciesToDependent.get(member);
            if (deps == null) {
              deps = new HashSet<PsiMember>();
              myDependenciesToDependent.put(member, deps);
            }
            deps.add(aDependent);
          }
        }
      }
    }

    return myDependencies;
  }

  public Set<? extends PsiMember> getDependenciesOf(PsiMember member) {
    final Set<? extends PsiMember> dependent = getDependent();
    if(!dependent.contains(member)) return null;
    return myDependenciesToDependent.get(member);
  }

  public String getElementTooltip(PsiMember element) {
    final Set<? extends PsiMember> dependencies = getDependenciesOf(element);
    if (dependencies == null || dependencies.size() == 0) return null;

    ArrayList<String> strings = new ArrayList<String>();
    for (PsiMember dep : dependencies) {
      if (dep instanceof PsiNamedElement) {
        strings.add(dep.getName());
      }
    }

    if (strings.isEmpty()) return null;
    return RefactoringBundle.message("uses.0", StringUtil.join(strings, ", "));
  }
}
