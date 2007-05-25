package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiElement;

import java.util.ArrayList;

public class RefactoringElementListenerComposite implements RefactoringElementListener {
  private final ArrayList<RefactoringElementListener> myListeners = new ArrayList<RefactoringElementListener>();

  public void addListener(final RefactoringElementListener listener){
    myListeners.add(listener);
  }

  public void elementMoved(final PsiElement newElement){
    for (RefactoringElementListener myListener : myListeners) {
      myListener.elementMoved(newElement);
    }
  }

  public void elementRenamed(final PsiElement newElement){
    for (RefactoringElementListener myListener : myListeners) {
      myListener.elementRenamed(newElement);
    }
  }
}
