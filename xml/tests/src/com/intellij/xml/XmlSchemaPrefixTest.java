/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xml;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xml.refactoring.SchemaPrefixRenameHandler;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Konstantin Bulenkov
 */
@TestApplication
public class XmlSchemaPrefixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/xml/tests/testData/schemaPrefix");

  @Test
  public void testPrefixUsages() {
    doFindUsages("usages.xml", 16);
    doFindUsages("usages1.xml", 16);
  }

  @Test
  public void testRename() {
    doRename();
  }
  @Test
  public void testRename1() {
    doRename();
  }

  @Test
  public void testRename2() {
    doRename();
  }
  @Test
  public void testRenameFromClosingTag() {
    doRename();
  }

  private void doRename() {
    doRename("xsd");
  }

  private void doRename(String newValue) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = myFixture.getTestName(true);
    EdtTestUtil.runInEdtAndWait(() -> CodeInsightTestUtil.doInlineRenameTest(new SchemaPrefixRenameHandler(), name, "xml", newValue, myFixture));
  }

  protected void doFindUsages(String filename, int usages) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertThat(myFixture.testFindUsages(filename)).hasSize(usages);
  }

}
