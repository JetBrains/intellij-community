package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class UsedByMemberDependencyGraph implements MemberDependencyGraph {
  protected HashSet mySelectedNormal;
  protected HashSet mySelectedAbstract;
  protected HashSet myMembers;
  protected HashSet myDependencies = null;
  protected com.intellij.util.containers.HashMap myDependenciesToDependent = null;
  private final MemberDependenciesStorage myMemberDependenciesStorage;

  UsedByMemberDependencyGraph(PsiClass aClass) {
    myMemberDependenciesStorage = new MemberDependenciesStorage(aClass, null);
    mySelectedNormal = new HashSet();
    mySelectedAbstract = new HashSet();
    myMembers = new HashSet();
  }

  public void memberChanged(MemberInfo memberInfo) {
    if(ClassMembersUtil.isProperMember(memberInfo))
    {
      myDependencies = null;
      myDependenciesToDependent = null;
      PsiElement member = memberInfo.getMember();
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

  public Set getDependent() {
    if(myDependencies == null) {
      myDependencies = new HashSet();
      myDependenciesToDependent = new com.intellij.util.containers.HashMap();
      for (Iterator iterator = myMembers.iterator(); iterator.hasNext();) {
        PsiElement element = (PsiElement) iterator.next();
        Set dependent = myMemberDependenciesStorage.getMemberDependencies(element);
        for (Iterator iterator1 = dependent.iterator(); iterator1.hasNext();) {
          PsiElement dependentOn = (PsiElement) iterator1.next();
          if(mySelectedNormal.contains(dependentOn) && !mySelectedAbstract.contains(dependentOn)) {
            myDependencies.add(element);
            HashSet deps = (HashSet) myDependenciesToDependent.get(element);
            if(deps == null) {
              deps = new HashSet();
              myDependenciesToDependent.put(element, deps);
            }
            deps.add(dependentOn);
          }
        }
      }
    }

    return myDependencies;
  }

  public Set getDependenciesOf(PsiElement element) {
    final Set dependent = getDependent();
    if(!dependent.contains(element)) return null;
    return (Set) myDependenciesToDependent.get(element);
  }

  public String getElementTooltip(PsiElement element) {
    final Set dependencies = getDependenciesOf(element);
    if (dependencies == null || dependencies.size() == 0) return null;

    ArrayList strings = new ArrayList();
    for (Iterator iterator = dependencies.iterator(); iterator.hasNext();) {
      PsiElement dep = (PsiElement) iterator.next();
      if (dep instanceof PsiNamedElement) {
        final String name = ((PsiNamedElement) dep).getName();
        if (name != null) {
          strings.add(name);
        }
      }
    }

    if (strings.isEmpty()) return null;
    StringBuffer buffer = new StringBuffer("uses ");
    final int size = strings.size();
    for (int i = 0; i < size; i++) {
      String s = (String) strings.get(i);
      buffer.append(s);
      if (i < size - 1) {
        buffer.append(", ");
      }
    }
    return buffer.toString();
  }

}
