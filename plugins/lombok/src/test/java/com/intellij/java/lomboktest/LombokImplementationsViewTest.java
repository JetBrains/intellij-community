// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lomboktest;

import com.intellij.codeInsight.ShowImplementationsTestUtil;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;


public class LombokImplementationsViewTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  public void testFromCompletionForBuilder() {
    @Language("JAVA")
    final String text = """
      @lombok.Builder
      public class Test {
          private int someInt;
          public static void main(String[] args) {
              Test test = Test.builder().someInt<caret>(1).build();
              System.out.println(test);
          }}""";
    PsiFile file = myFixture.addFileToProject("Test.java", text);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    PsiElement[] elements = ShowImplementationsTestUtil.getImplementations();
    assertNotNull(elements);
    assertEquals(1, elements.length);
    assertTrue(elements[0] instanceof PsiField);
    final String newText = ImplementationViewComponent.getNewText(elements[0]);
    assertEquals("    private int someInt;", newText);
  }

  public void testFromCompletionForData() {
    @Language("JAVA")
    final String text = """
      @lombok.Data
      public class Test {
          private String someString;
          public static void main(String[] args) {
              Test test = new Test();
              test.setSomeString<caret>("Hello"));
          }}""";
    PsiFile file = myFixture.addFileToProject("Test.java", text);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    PsiElement[] elements = ShowImplementationsTestUtil.getImplementations();
    assertNotNull(elements);
    assertEquals(1, elements.length);
    assertTrue(elements[0] instanceof PsiField);
    final String newText = ImplementationViewComponent.getNewText(elements[0]);
    assertEquals("    private String someString;", newText);
  }
}
