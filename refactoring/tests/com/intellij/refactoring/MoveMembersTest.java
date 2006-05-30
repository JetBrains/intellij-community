package com.intellij.refactoring;

import com.intellij.idea.Bombed;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.move.moveMembers.MockMoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;

public class MoveMembersTest extends MultiFileTestCase {
  @Bombed(user = "lesya", day = 30, month = Calendar.JUNE, description = "Need to fix javadoc formatter", year = 2006, time = 15)
  public void testJavadocRefs() throws Exception {
    doTest("Class1", "Class2", new int[]{0});
  }

  public void testWeirdDeclaration() throws Exception {
    doTest("A", "B", new int[]{0});
  }

  public void testInnerClass() throws Exception {
    doTest("A", "B", new int[]{0});
  }

  public void testScr11871() throws Exception {
    doTest("pack1.A", "pack1.B", new int[]{0});
  }

  public void testOuterClassTypeParameters() throws Exception {
    doTest("pack1.A", "pack2.B", new int[]{0});
  }

  public void testscr40064() throws Exception {
    doTest("Test", "Test1", new int[]{0});
  }

  public void testscr40947() throws Exception {
    doTest("A", "Test", new int[]{0, 1});
  }

  public void testTwoMethods() throws Exception {
    doTest("pack1.A", "pack1.C", new int[]{0, 1, 2});
  }

  protected String getTestRoot() {
    return "/refactoring/moveMembers/";
  }

  private void doTest(final String sourceClassName, final String targetClassName, final int[] memberIndices)
    throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        MoveMembersTest.this.performAction(sourceClassName, targetClassName, memberIndices);
      }
    });
  }

  private void performAction(String sourceClassName, String targetClassName, int[] memberIndices) throws Exception {
    PsiClass sourceClass = myPsiManager.findClass(sourceClassName);
    assertNotNull("Class " + sourceClassName + " not found", sourceClass);
    PsiClass targetClass = myPsiManager.findClass(targetClassName);
    assertNotNull("Class " + targetClassName + " not found", targetClass);

    PsiElement[] children = sourceClass.getChildren();
    ArrayList<PsiMember> members = new ArrayList<PsiMember>();
    for (PsiElement child : children) {
      if (child instanceof PsiMember) {
        members.add(((PsiMember) child));
      }
    }

    LinkedHashSet<PsiMember> memberSet = new LinkedHashSet<PsiMember>();
    for (int index : memberIndices) {
      PsiMember member = members.get(index);
      assertTrue(member.hasModifierProperty(PsiModifier.STATIC));
      memberSet.add(member);
    }

    MockMoveMembersOptions options = new MockMoveMembersOptions(targetClass.getQualifiedName(), memberSet);
    options.setMemberVisibility(null);
    new MoveMembersProcessor(myProject, null, options).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
