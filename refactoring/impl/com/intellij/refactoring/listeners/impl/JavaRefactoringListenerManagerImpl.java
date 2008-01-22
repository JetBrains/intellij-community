package com.intellij.refactoring.listeners.impl;

import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author yole
 */
public class JavaRefactoringListenerManagerImpl extends JavaRefactoringListenerManager {
  private List<MoveMemberListener> myMoveMemberListeners = new CopyOnWriteArrayList<MoveMemberListener>();

  public void addMoveMembersListener(MoveMemberListener moveMembersListener) {
    myMoveMemberListeners.add(moveMembersListener);
  }

  public void removeMoveMembersListener(MoveMemberListener moveMembersListener) {
    myMoveMemberListeners.remove(moveMembersListener);
  }

  public void fireMemberMoved(final PsiClass sourceClass, final PsiMember member) {
    for (final MoveMemberListener listener : myMoveMemberListeners) {
      listener.memberMoved(sourceClass, member);
    }
  }
}
