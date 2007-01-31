package com.intellij.refactoring.listeners.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.impl.RefactoringTransactionImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author dsl
 */
public class RefactoringListenerManagerImpl extends RefactoringListenerManager {
  private final ArrayList<RefactoringElementListenerProvider> myListenerProviders;
  private final Project myProject;
  private List<MoveMemberListener> myMoveMemberListeners = new CopyOnWriteArrayList<MoveMemberListener>();

  public RefactoringListenerManagerImpl(Project project) {
    myProject = project;
    myListenerProviders = new ArrayList<RefactoringElementListenerProvider>();
  }

  public void addListenerProvider(RefactoringElementListenerProvider provider) {
    myListenerProviders.add(provider);
  }

  public void removeListenerProvider(RefactoringElementListenerProvider provider) {
    myListenerProviders.remove(provider);
  }

  public RefactoringTransaction startTransaction() {
    return new RefactoringTransactionImpl(myListenerProviders);
  }

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
