package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.util.containers.HashMap;

import java.util.HashSet;


class MemberDependenciesStorage {
  protected final PsiClass myClass;
  private final PsiClass mySuperClass;
  private HashMap myDependencyGraph;

  MemberDependenciesStorage(PsiClass aClass, PsiClass superClass) {
    myClass = aClass;
    mySuperClass = superClass;
    myDependencyGraph = new HashMap();
  }

  protected HashSet getMemberDependencies(PsiElement member) {
    HashSet result = (HashSet) myDependencyGraph.get(member);
    if (result == null) {
      DependentMembersCollector collector = new DependentMembersCollector();
      member.accept(collector);
      result = collector.getCollection();
      myDependencyGraph.put(member, result);
    }
    return result;
  }

  class DependentMembersCollector extends ClassMemberReferencesVisitor {
    private HashSet myCollection = new HashSet();

    DependentMembersCollector() {
      super(myClass);
    }

    public HashSet getCollection() {
      return myCollection;
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if (!existsInSuperClass(classMember)) {
        myCollection.add(classMember);
      }
    }
  }

  private boolean existsInSuperClass(PsiElement classMember) {
    if(mySuperClass == null) return false;
    if (!(classMember instanceof PsiMethod)) return false;
    final PsiMethod method = ((PsiMethod) classMember);
    final PsiMethod methodBySignature = mySuperClass.findMethodBySignature(method, true);
    return methodBySignature != null;
  }
}
