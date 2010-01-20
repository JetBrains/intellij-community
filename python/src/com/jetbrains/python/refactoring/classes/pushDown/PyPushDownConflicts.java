package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownConflicts {
  private final PyClass myClass;
  private final Collection<PyMemberInfo> myMembers;
  private MultiMap<PsiElement, String> myConflicts;

  public PyPushDownConflicts(final PyClass clazz, final Collection<PyMemberInfo> members) {
    myClass = clazz;
    myMembers = members;
    myConflicts = new MultiMap<PsiElement, String>();
  }

  public MultiMap<PsiElement, String> getConflicts() {
    return myConflicts;
  }

  public void checkTargetClassConflicts(PsiElement element) {

  }

  public void checkSourceClassConflicts() {
    
  }
}
