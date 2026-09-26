// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import com.siyeh.ig.testFrameworks.AssertWithoutMessageInspection;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;

/**
 * @author Bas Leijdekkers
 */
public class AssertsWithoutMessagesTestNGInspectionTest extends JavaCodeInsightFixtureTestCase {

  public void testInspection() {
    myFixture.enableInspections(AssertWithoutMessageInspection.class);
    myFixture.testHighlighting("AssertsWithoutMessages.java");
  }

  public void testQuickFix() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import org.testng.annotations.Test;
      import static org.testng.Assert.*;
      class TestCase {
          @Test
          public void test() {
              <warning descr="'assertEquals()' without message"><caret>assertEquals</warning>(1, 1);
          }
      }""");
    myFixture.enableInspections(AssertWithoutMessageInspection.class);
    myFixture.testHighlighting(true, false, false);

    final IntentionAction intention = myFixture.getAvailableIntention("Add error message");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResult("""
                            import org.testng.annotations.Test;
                            import static org.testng.Assert.*;
                            class TestCase {
                                @Test
                                public void test() {
                                    assertEquals(1, 1, "<caret>");
                                }
                            }""");
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("testng", PathUtil.getJarPathForClass(DataProvider.class));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("testng") + "/testData/inspection/asserts_without_messages/";
  }
}