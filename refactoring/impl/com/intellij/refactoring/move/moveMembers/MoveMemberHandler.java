package com.intellij.refactoring.move.moveMembers;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public interface MoveMemberHandler {
  LanguageExtension<MoveMemberHandler> EP_NAME = new LanguageExtension<MoveMemberHandler>("com.intellij.refactoring.moveMemberHandler");

  MoveMembersProcessor.MoveMembersUsageInfo getUsage(PsiMember member,
                                                     PsiReference ref,
                                                     Set<PsiMember> membersToMove,
                                                     PsiClass targetClass);

  boolean changeExternalUsage(MoveMembersOptions options, MoveMembersProcessor.MoveMembersUsageInfo usage);

  PsiMember doMove(MoveMembersOptions options, PsiMember member, ArrayList<MoveMembersProcessor.MoveMembersUsageInfo> otherUsages);

  void decodeContextInfo(PsiElement scope);
}
