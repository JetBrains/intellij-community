package com.intellij.refactoring.listeners.impl.impl;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author dsl
 */
public class RefactoringTransactionImpl implements RefactoringTransaction {
  /**
   * Actions to be performed at commit.
   */
  private final ArrayList<Runnable> myRunnables = new ArrayList<Runnable>();
  private final List<RefactoringElementListenerProvider> myListenerProviders;
  private final HashMap<PsiElement,ArrayList<RefactoringElementListener>> myOldElementToListenerListMap = new com.intellij.util.containers.HashMap<PsiElement, ArrayList<RefactoringElementListener>>();
  private final HashMap<PsiElement,RefactoringElementListener> myOldElementToTransactionListenerMap = new com.intellij.util.containers.HashMap<PsiElement, RefactoringElementListener>();

  public RefactoringTransactionImpl(List<RefactoringElementListenerProvider> listenerProviders) {
    myListenerProviders = listenerProviders;
  }

  private void addAffectedElement(PsiElement oldElement) {
    if(myOldElementToListenerListMap.get(oldElement) != null) return;
    ArrayList<RefactoringElementListener> listenerList = new ArrayList<RefactoringElementListener>();
    for (int i = 0; i < myListenerProviders.size(); i++) {
      RefactoringElementListenerProvider provider = myListenerProviders.get(i);
      final RefactoringElementListener listener = provider.getListener(oldElement);
      if(listener != null) {
        listenerList.add(listener);
      }
    }
    myOldElementToListenerListMap.put(oldElement, listenerList);
  }


  public RefactoringElementListener getElementListener(PsiElement oldElement) {
    RefactoringElementListener listener =
      myOldElementToTransactionListenerMap.get(oldElement);
    if(listener == null) {
      listener = new MyRefactoringElementListener(oldElement);
      myOldElementToTransactionListenerMap.put(oldElement, listener);
    }
    return listener;
  }

  private class MyRefactoringElementListener implements RefactoringElementListener {
    private final ArrayList<RefactoringElementListener> myListenerList;
    private MyRefactoringElementListener(PsiElement oldElement) {
      addAffectedElement(oldElement);
      myListenerList = myOldElementToListenerListMap.get(oldElement);
    }

    public void elementMoved(final PsiElement newElement) {
      myRunnables.add(
              new Runnable() {
                public void run() {
                  for (Iterator<RefactoringElementListener> iterator = myListenerList.iterator(); iterator.hasNext();) {
                    RefactoringElementListener refactoringElementListener = iterator.next();
                    refactoringElementListener.elementMoved(newElement);
                  }
                }
              }
      );
    }

    public void elementRenamed(final PsiElement newElement) {
      myRunnables.add(
              new Runnable() {
                public void run() {
                  for (Iterator<RefactoringElementListener> iterator = myListenerList.iterator(); iterator.hasNext();) {
                    RefactoringElementListener refactoringElementListener = iterator.next();
                    refactoringElementListener.elementRenamed(newElement);
                  }
                }
              }
      );
    }
  }

  public void commit() {
    for (int i = 0; i < myRunnables.size(); i++) {
      Runnable runnable = myRunnables.get(i);
      runnable.run();
    }
  }

}
