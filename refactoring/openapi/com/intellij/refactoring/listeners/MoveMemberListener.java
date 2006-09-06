package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;

/**
 * Notifies that a certain member has been moved.
 * This listener is invoked by pull up, push down and extract super refactorings.
 * To subscribe to move refactoring use {@link com.intellij.refactoring.listeners.RefactoringElementListener} class.
 * @author ven
 */
public interface MoveMemberListener {
  /**
   * @param sourceClass the class member was in before the refactoring
   * @param member the member that has been moved. To obtain target class use
   * {@link com.intellij.psi.PsiMember#getContainingClass()}. In all cases but
   * "Move inner to upper level" target class wil be non null.
   */
  void memberMoved (PsiClass sourceClass, PsiMember member);
}
