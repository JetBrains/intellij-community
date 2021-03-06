/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PluginPathManager;
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
    myFixture.configureByText(JavaFileType.INSTANCE, "import org.testng.annotations.Test;\n" +
                                          "import static org.testng.Assert.*;\n" +

                                          "class TestCase {\n" +
                                          "    @Test\n" +
                                          "    public void test() {\n" +
                                          "        <warning descr=\"'assertEquals()' without message\"><caret>assertEquals</warning>(1, 1);\n" +
                                          "    }\n" +
                                          "}");
    myFixture.enableInspections(AssertWithoutMessageInspection.class);
    myFixture.testHighlighting(true, false, false);

    final IntentionAction intention = myFixture.getAvailableIntention("Add error message");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("import org.testng.annotations.Test;\n" +
                          "import static org.testng.Assert.*;\n" +

                          "class TestCase {\n" +
                          "    @Test\n" +
                          "    public void test() {\n" +
                          "        assertEquals(1, 1, \"<caret>\");\n" +
                          "    }\n" +
                          "}");
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