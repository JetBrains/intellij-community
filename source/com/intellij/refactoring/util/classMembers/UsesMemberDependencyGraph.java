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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class UsesMemberDependencyGraph implements MemberDependencyGraph {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.classMembers.UsesMemberDependencyGraph");
  protected HashSet mySelectedNormal;
  protected HashSet mySelectedAbstract;
  protected HashSet myDependencies = null;
  protected com.intellij.util.containers.HashMap myDependenciesToDependentMap = null;
  private final PsiClass mySuperClass;
  private final boolean myRecursive;
  private MemberDependenciesStorage myMemberDependenciesStorage;
  private PsiClass myClass;

  public UsesMemberDependencyGraph(PsiClass aClass, PsiClass superClass, boolean recursive) {
    mySuperClass = superClass;
    myRecursive = recursive;
    mySelectedNormal = new HashSet();
    mySelectedAbstract = new HashSet();
    myClass = aClass;
    myMemberDependenciesStorage = new MemberDependenciesStorage(myClass, superClass);
  }


  public Set getDependent() {
    if (myDependencies == null) {
      myDependencies = new HashSet();
      myDependenciesToDependentMap = new com.intellij.util.containers.HashMap();
      buildDeps(null, mySelectedNormal, myDependencies, myDependenciesToDependentMap, true);
    }
    return myDependencies;
  }

  public Set getDependenciesOf(PsiElement element) {
    final Set dependent = getDependent();
    if(!dependent.contains(element)) return null;
    return (Set) myDependenciesToDependentMap.get(element);
  }

  public String getElementTooltip(PsiElement element) {
    final Set dependencies = getDependenciesOf(element);
    if(dependencies == null || dependencies.size() == 0) return null;

    ArrayList strings = new ArrayList();
    for (Iterator iterator = dependencies.iterator(); iterator.hasNext();) {
      PsiElement dep = (PsiElement) iterator.next();
      if(dep instanceof PsiNamedElement) {
        final String name = ((PsiNamedElement) dep).getName();
        if(name != null) {
          strings.add(name);
        }
      }
    }

    if(strings.isEmpty()) return null;
    StringBuffer buffer = new StringBuffer("used by ");
    final int size = strings.size();
    for (int i = 0; i < size; i++) {
      String s = (String) strings.get(i);
      buffer.append(s);
      if(i < size - 1) {
        buffer.append(", ");
      }
    }
    return buffer.toString();
  }


  protected void buildDeps(PsiElement sourceElement, Set elements, Set result, com.intellij.util.containers.HashMap relationMap, boolean recurse) {
    for (Iterator iterator = elements.iterator(); iterator.hasNext();) {
      PsiElement element = (PsiElement) iterator.next();

      if (!result.contains(element)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(element.toString());
        }
        result.add(element);
        if (sourceElement != null) {
          HashSet relations = (HashSet) relationMap.get(element);
          if(relations == null) {
            relations = new HashSet();
            relationMap.put(element, relations);
          }
          relations.add(sourceElement);
        }
        if (recurse && !mySelectedAbstract.contains(element)) {
          buildDeps(element, myMemberDependenciesStorage.getMemberDependencies(element), result, relationMap, myRecursive);
        }
      }
    }
  }

  public void memberChanged(MemberInfo memberInfo) {
    if (ClassMembersUtil.isProperMember(memberInfo)) {
      myDependencies = null;
      myDependenciesToDependentMap = null;
      PsiElement member = memberInfo.getMember();
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
