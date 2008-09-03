package com.intellij.refactoring.move.moveMembers;

import com.intellij.psi.PsiMember;

/**
 * @author dyoma
 */
public interface MoveMembersOptions {
  PsiMember[] getSelectedMembers();

  String getTargetClassName();

  String getMemberVisibility();

  boolean makeEnumConstant();
}
