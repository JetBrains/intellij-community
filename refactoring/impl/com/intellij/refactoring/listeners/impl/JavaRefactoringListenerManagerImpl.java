package com.intellij.refactoring.listeners.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author yole
 */
public class JavaRefactoringListenerManagerImpl extends JavaRefactoringListenerManager {
  private final List<MoveMemberListener> myMoveMemberListeners = ContainerUtil.createEmptyCOWList();

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
