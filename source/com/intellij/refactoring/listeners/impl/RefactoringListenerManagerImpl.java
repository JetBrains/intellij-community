package com.intellij.refactoring.listeners.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.impl.RefactoringTransactionImpl;

import java.util.ArrayList;

/**
 * @author dsl
 */
public class RefactoringListenerManagerImpl extends RefactoringListenerManager implements ProjectComponent {
  private final ArrayList<RefactoringElementListenerProvider> myListenerProviders;
  private final Project myProject;

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

  public String getComponentName() {
    return "RefactoringListenerManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
