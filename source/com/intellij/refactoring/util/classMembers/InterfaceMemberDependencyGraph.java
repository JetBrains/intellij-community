package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class InterfaceMemberDependencyGraph implements MemberDependencyGraph {
  protected HashSet myInterfaceDependencies = null;
  protected com.intellij.util.containers.HashMap myMembersToInterfacesMap = new com.intellij.util.containers.HashMap();
  protected HashSet myImplementedInterfaces;
  protected com.intellij.util.containers.HashMap myMethodsFromInterfaces;
  protected PsiClass myClass;

  public InterfaceMemberDependencyGraph(PsiClass aClass) {
    myClass = aClass;
    myImplementedInterfaces = new HashSet();
    myMethodsFromInterfaces = new com.intellij.util.containers.HashMap();
  }

  public void memberChanged(MemberInfo memberInfo) {
    if (ClassMembersUtil.isImplementedInterface(memberInfo)) {
      final PsiClass aClass = (PsiClass) memberInfo.getMember();
      myInterfaceDependencies = null;
      myMembersToInterfacesMap = null;
      if(memberInfo.isChecked()) {
        myImplementedInterfaces.add(aClass);
      }
      else {
        myImplementedInterfaces.remove(aClass);
      }
    }
  }

  public Set getDependent() {
    if(myInterfaceDependencies == null) {
      myInterfaceDependencies = new HashSet();
      myMembersToInterfacesMap = new com.intellij.util.containers.HashMap();
      for (Iterator iterator = myImplementedInterfaces.iterator(); iterator.hasNext();) {
        addInterfaceDeps((PsiClass) iterator.next());
      }
    }
    return myInterfaceDependencies;
  }

  public Set getDependenciesOf(PsiElement element) {
    final Set dependent = getDependent();
    if(dependent.contains(element)) return (Set) myMembersToInterfacesMap.get(element);
    return null;
  }

  public String getElementTooltip(PsiElement element) {
    final Set dependencies = getDependenciesOf(element);
    if(dependencies == null || dependencies.size() == 0) return null;
    StringBuffer buffer = new StringBuffer("required by ");
    if(dependencies.size() == 1) {
      buffer.append("interface ");
    } else {
      buffer.append("intefaces ");
    }

    for (Iterator iterator = dependencies.iterator(); iterator.hasNext();) {
      PsiClass aClass = (PsiClass) iterator.next();
      buffer.append(aClass.getName());
      if(iterator.hasNext()) {
        buffer.append(", ");
      }
    }
    return buffer.toString();
  }

  protected void addInterfaceDeps(PsiClass intf) {
    HashSet interfaceMethods = (HashSet) myMethodsFromInterfaces.get(intf);

    if(interfaceMethods == null) {
      interfaceMethods = new HashSet();
      buildInterfaceMethods(interfaceMethods, intf);
      myMethodsFromInterfaces.put(intf, interfaceMethods);
    }
    for (Iterator iterator = interfaceMethods.iterator(); iterator.hasNext();) {
      PsiMethod method = (PsiMethod) iterator.next();
      HashSet interfaces = (HashSet) myMembersToInterfacesMap.get(method);
      if(interfaces == null) {
        interfaces = new HashSet();
        myMembersToInterfacesMap.put(method, interfaces);
      }
      interfaces.add(intf);
    }
    myInterfaceDependencies.addAll(interfaceMethods);
  }

  private void buildInterfaceMethods(HashSet interfaceMethods, PsiClass intf) {
    PsiMethod[] methods = intf.getMethods();
    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = myClass.findMethodBySignature(methods[i], true);
      if(method != null) {
        interfaceMethods.add(method);
      }
    }

    PsiReferenceList implementsList = intf.getImplementsList();
    if (implementsList != null) {
      PsiClassType[] implemented = implementsList.getReferencedTypes();
      for (int i = 0; i < implemented.length; i++) {
        PsiClass resolved = implemented[i].resolve();
        if(resolved != null) {
          buildInterfaceMethods(interfaceMethods, resolved);
        }
      }
    }

    PsiReferenceList extendsList = intf.getExtendsList();
    if (extendsList != null) {
      PsiClassType[] extended = extendsList.getReferencedTypes();
      for (int i = 0; i < extended.length; i++) {
        PsiClass ref = extended[i].resolve();
        if (ref != null) {
          buildInterfaceMethods(interfaceMethods, ref);
        }
      }
    }
  }

}
