/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.refactoring;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.util.Pair;

/**
 * @author ven
 */
public class PullUpTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/pullUp/";


  public void testQualifiedThis() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>> ("Inner", PsiClass.class));
  }

  public void testQualifiedSuper() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>> ("Inner", PsiClass.class));
  }

  private void doTest(Pair<String, Class<? extends PsiMember>>... membersToFind) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    final PsiClass sourceClass = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    assertNotNull(sourceClass);

    PsiClass targetClass = sourceClass.getSuperClass();
    assertTrue(targetClass.isWritable());
    MemberInfo[] infos = new MemberInfo[membersToFind.length];
    for (int i = 0; i < membersToFind.length; i++) {
      final Class<? extends PsiMember> clazz = membersToFind[i].getSecond();
      final String name = membersToFind[i].getFirst();
      PsiMember member = null;
      if (PsiClass.class.isAssignableFrom(clazz)) {
        member = sourceClass.findInnerClassByName(name, false);
      } else if (PsiMethod.class.isAssignableFrom(clazz)) {
        final PsiMethod[] methods = sourceClass.findMethodsByName(name, false);
        assertEquals(1, methods.length);
        member = methods[0];
      } else if (PsiField.class.isAssignableFrom(clazz)) {
        member = sourceClass.findFieldByName(name, false);
      }

      assertNotNull(member);
      infos[i] = new MemberInfo(member);
    }

    final int[] countMoved = new int[] {0};
    final MoveMemberListener listener = new MoveMemberListener() {
      public void memberMoved(PsiClass aClass, PsiMember member) {
        assertEquals(sourceClass, aClass);
        countMoved[0]++;
      }
    };
    RefactoringListenerManager.getInstance(getProject()).addMoveMembersListener(listener);
    new PullUpHelper(sourceClass, targetClass, infos, new JavaDocPolicy(JavaDocPolicy.ASIS)).moveMembersToBase();
    RefactoringListenerManager.getInstance(getProject()).removeMoveMembersListener(listener);
    assertEquals(countMoved[0], membersToFind.length);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
