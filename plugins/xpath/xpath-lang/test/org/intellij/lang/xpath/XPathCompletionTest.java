/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.lang.xpath;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;

@TestApplication
public class XPathCompletionTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XPathTestUtil.getTestDataPath("xpath/completion"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
  }

  @Test
  public void testAxis() {
        doXPathCompletion("ancestor", "ancestor-or-self", "attribute");
    }

    @Test
    public void testAxisInsert() {
        doXPathCompletion();
    }

    @Test
    public void testPartialAxis() {
        doXPathCompletion("ancestor", "ancestor-or-self");
    }

    @Test
    public void testFunctions() {
        doXPathCompletion("text()", "translate(string, string, string)", "true()");
    }

    @Test
    public void testFunctionInsert1() {
        doXPathCompletion();
    }

    @Test
    public void testFunctionInsert2() {
        doXPathCompletion();
    }

    private void doXPathCompletion() {
        CodeInsightTestFixture myFixture = codeInsightFixture.get();
        final String name = testFileName;
        myFixture.configureByFile(name + ".xpath");
        if (myFixture.completeBasic() != null) {
          myFixture.type('\n');
        }
        myFixture.checkResultByFile(name + "_after.xpath");
    }

    private void doXPathCompletion(String... expectedVariants) {
        CodeInsightTestFixture myFixture = codeInsightFixture.get();
        myFixture.configureByFile(testFileName + ".xpath");
        myFixture.completeBasic();
        myFixture.assertPreferredCompletionItems(0, expectedVariants);
    }
}