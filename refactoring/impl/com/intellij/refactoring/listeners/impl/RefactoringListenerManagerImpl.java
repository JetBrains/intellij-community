package com.intellij.refactoring.listeners.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.refactoring.listeners.impl.impl.RefactoringTransactionImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class RefactoringListenerManagerImpl extends RefactoringListenerManager implements ProjectComponent {
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

  //
  // ProjectComponent implementation
  //

  public void projectOpened() {
    // do nothing
  }

  public void projectClosed() {
    // do nothing
  }

  @NotNull
  public String getComponentName() {
    return "RefactoringListenerManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
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
