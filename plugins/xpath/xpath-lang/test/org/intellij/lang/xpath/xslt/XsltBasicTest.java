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
package org.intellij.lang.xpath.xslt;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.xpath.XPathTestUtil;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class XsltBasicTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;
  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XPathTestUtil.getTestDataPath("xslt"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
    testName = testInfo.getTestMethod().orElseThrow().getName();
  }

  @Test
  public void testSupportedXslt10() {
    doTestXsltSupport();
  }

  @Test
  public void testSupportedXslt10_Loaded() {
    doTestXsltSupport();
  }

  @Test
  public void testSupportedXslt11() {
    doTestXsltSupport();
  }

  @Test
  public void testSupportedSimplifiedXslt() {
    doTestXsltSupport();
  }

  @Test
  public void testSupportedSimplifiedXslt_Loaded() {
    doTestXsltSupport();
  }

  @Test
  public void testSupportedXslt20() {
    doTestXsltSupport();
  }

  @Test
  public void testUnsupportedXsltNoVersion() {
    doTestXsltSupport();
  }

  @Test
  public void testUnsupportedXsltNoVersion_Loaded() {
    doTestXsltSupport();
  }

  @Test
  public void testUnsupportedNoXslt() {
    doTestXsltSupport();
  }

  @Test
  public void testUnsupportedNoXslt_Loaded() {
    doTestXsltSupport();
  }

  @Test
  public void testUnsupportedNoXslt2() {
    doTestXsltSupport();
  }

  // actually a PSI test: IDEADEV-35024
  @Test
  public void testUnsupportedNoXslt2_Loaded() {
    doTestXsltSupport();
  }

  private void doTestXsltSupport() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure();

    ReadAction.runBlocking(() -> {
      final XsltChecker.LanguageLevel level = XsltSupport.getXsltLanguageLevel(myFixture.getFile());
      if (level != XsltChecker.LanguageLevel.NONE) {
        assertTrue(testName.contains("Supported"));
        assertTrue(XsltSupport.isXsltFile(myFixture.getFile()));
      } else {
        assertTrue(testName.contains("Unsupported"));
        assertFalse(XsltSupport.isXsltFile(myFixture.getFile()));
      }
    });
  }

  private void configure() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String fileName = testFileName;
    String path = fileName.replaceAll("_.*$", "") + ".xsl";
    final VirtualFile file = myFixture.copyFileToProject(path);
    EdtTestUtil.runInEdtAndWait(() -> myFixture.openFileInEditor(file));
    ReadAction.runBlocking(() -> {
      if (fileName.endsWith("_Loaded")) {
        ((XmlFile)myFixture.getFile()).getDocument();
        assertTrue(((PsiFileEx)myFixture.getFile()).isContentsLoaded());
      } else {
        assertFalse(((PsiFileEx)myFixture.getFile()).isContentsLoaded());
      }
    });
  }
}
