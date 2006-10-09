/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 08.07.2002
 * Time: 18:22:48
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class UsesMemberDependencyGraph implements MemberDependencyGraph {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.classMembers.UsesMemberDependencyGraph");
  protected HashSet<PsiMember> mySelectedNormal;
  protected HashSet<PsiMember> mySelectedAbstract;
  protected HashSet<PsiMember> myDependencies = null;
  protected HashMap<PsiMember,HashSet<PsiMember>> myDependenciesToDependentMap = null;
  private final boolean myRecursive;
  private MemberDependenciesStorage myMemberDependenciesStorage;

  public UsesMemberDependencyGraph(PsiClass aClass, PsiClass superClass, boolean recursive) {
    myRecursive = recursive;
    mySelectedNormal = new HashSet<PsiMember>();
    mySelectedAbstract = new HashSet<PsiMember>();
    myMemberDependenciesStorage = new MemberDependenciesStorage(aClass, superClass);
  }


  public Set<? extends PsiMember> getDependent() {
    if (myDependencies == null) {
      myDependencies = new HashSet<PsiMember>();
      myDependenciesToDependentMap = new HashMap<PsiMember, HashSet<PsiMember>>();
      buildDeps(null, mySelectedNormal);
    }
    return myDependencies;
  }

  public Set<? extends PsiMember> getDependenciesOf(PsiMember member) {
    final Set dependent = getDependent();
    if(!dependent.contains(member)) return null;
    return myDependenciesToDependentMap.get(member);
  }

  public String getElementTooltip(PsiMember element) {
    final Set<? extends PsiMember> dependencies = getDependenciesOf(element);
    if(dependencies == null || dependencies.size() == 0) return null;

    ArrayList<String> strings = new ArrayList<String>();
    for (PsiMember dep : dependencies) {
      strings.add(dep.getName());
    }

    if(strings.isEmpty()) return null;
    return RefactoringBundle.message("used.by.0", StringUtil.join(strings, ", "));
  }


  private void buildDeps(PsiMember sourceElement, Set<PsiMember> members) {
    if (myRecursive) {
      buildDepsRecursively(sourceElement, members);
    }
    else {
      for (final PsiMember member : members) {
        for (final PsiMember dependency : myMemberDependenciesStorage.getMemberDependencies(member)) {
          addDependency(dependency, member);
        }
      }
    }
  }

  private void buildDepsRecursively(final PsiMember sourceElement, final Set<PsiMember> members) {
    for (PsiMember member : members) {
      if (!myDependencies.contains(member)) {
        addDependency(member, sourceElement);
        if (!mySelectedAbstract.contains(member)) {
          buildDepsRecursively(member, myMemberDependenciesStorage.getMemberDependencies(member));
        }
      }
    }
  }

  private void addDependency(final PsiMember member, final PsiMember sourceElement) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(member.toString());
    }
    myDependencies.add(member);
    if (sourceElement != null) {
      HashSet<PsiMember> relations = myDependenciesToDependentMap.get(member);
      if (relations == null) {
        relations = new HashSet<PsiMember>();
        myDependenciesToDependentMap.put(member, relations);
      }
      relations.add(sourceElement);
    }
  }

  public void memberChanged(MemberInfo memberInfo) {
    if (ClassMembersUtil.isProperMember(memberInfo)) {
      myDependencies = null;
      myDependenciesToDependentMap = null;
      PsiMember member = memberInfo.getMember();
      if (!memberInfo.isChecked()) {
        mySelectedNormal.remove(member);
        mySelectedAbstract.remove(member);
      } else {
        if (memberInfo.isToAbstract()) {
          mySelectedNormal.remove(member);
          mySelectedAbstract.add(member);
        } else {
          mySelectedNormal.add(member);
          mySelectedAbstract.remove(member);
        }
      }
    }
  }
}
