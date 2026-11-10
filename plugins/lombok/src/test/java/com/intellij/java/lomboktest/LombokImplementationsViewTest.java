// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lomboktest;

import com.intellij.codeInsight.ShowImplementationsTestUtil;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LombokImplementationsViewTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testFromCompletionForBuilder() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    PsiElement[] elements = EdtTestUtil.runInEdtAndGet(() -> ShowImplementationsTestUtil.getImplementations());
    assertNotNull(elements);
    assertEquals(1, elements.length);
    assertTrue(elements[0] instanceof PsiField);
    final String newText = ReadAction.computeBlocking(() -> ImplementationViewComponent.getNewText(elements[0]));
    assertEquals("    private int someInt;", newText);
  }

  @Test
  public void testFromCompletionForData() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    PsiElement[] elements = EdtTestUtil.runInEdtAndGet(() -> ShowImplementationsTestUtil.getImplementations());
    assertNotNull(elements);
    assertEquals(1, elements.length);
    assertTrue(elements[0] instanceof PsiField);
    final String newText = ReadAction.computeBlocking(() -> ImplementationViewComponent.getNewText(elements[0]));
    assertEquals("    private String someString;", newText);
  }
}
