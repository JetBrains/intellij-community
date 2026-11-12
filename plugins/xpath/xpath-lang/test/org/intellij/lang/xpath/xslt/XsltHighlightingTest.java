// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt;

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnusedNamespaceInspection;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.TimeoutUtil;
import org.intellij.lang.xpath.XPathTestUtil;
import org.intellij.lang.xpath.xslt.impl.XsltStuffProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Collections;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class XsltHighlightingTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo, @TestDisposable Disposable disposable) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(XPathTestUtil.getTestDataPath("xslt/highlighting"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
    myFixture.enableInspections(XsltStuffProvider.INSPECTION_CLASSES);
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList("urn:my"), disposable);
  }

  public void xtestBackwardIncludedVariable() {
    doXsltHighlighting();
  }

  @Test
  public void testUnknownTemplate() {
    doXsltHighlighting();
  }

  @Test
  public void testDuplicateTemplate() {
    doXsltHighlighting();
  }

  @Test
  public void testUnknownMode() {
    doXsltHighlighting();
  }

  @Test
  public void testUndeclaredParam() {
    doXsltHighlighting();
  }

  @Test
  public void testMissingParam() {
    doXsltHighlighting();
  }

  @Test
  public void testUnusedVariable() {
    doXsltHighlighting();
  }

  @Test
  public void testDuplicateVariable() {
    doXsltHighlighting();
  }

  @Test
  public void testNonDuplicateVariable() {
    doXsltHighlighting();
  }

  @Test
  public void testShadowedVariable() {
    doXsltHighlighting();
  }

  @Test
  public void testShadowedVariable2() {
    doXsltHighlighting();
  }

  @Test
  public void testValidPatterns() {
    doXsltHighlighting();
  }

  @Test
  public void testInvalidPattern1() {
    doXsltHighlighting();
  }

  @Test
  public void testInvalidPattern2() {
    doXsltHighlighting();
  }

  @Test
  public void testInvalidPattern3() {
    doXsltHighlighting();
  }

  @Test
  public void testInvalidPattern4() {
    doXsltHighlighting();
  }

  @Test
  public void testInvalidPattern5() {
    doXsltHighlighting();
  }

  @Test
  public void testInvalidPattern6() {
    doXsltHighlighting();
  }

  @Test
  public void testEmptyExpression() {
    doXsltHighlighting();
  }

  @Test
  public void testEmptyAVT() {
    doXsltHighlighting();
  }

  @Test
  public void testInvalidSingleClosingBrace() {
    doXsltHighlighting();
  }

  @Test
  public void testEscapedXPathString() {
    doXsltHighlighting();
  }

  @Test
  public void testXsltFreeze() {
    doXsltHighlighting();
  }

  @Test
  public void testTemplateWithPrefix() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(XmlUnusedNamespaceInspection.class);
    doXsltHighlighting();
  }

  @PerformanceUnitTest
  public void xtestPerformance() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testFileName + ".xsl");
    final long l = runHighlighting();
    assertTrue(l < 6000, "Highlighting took " + l + "ms");
  }

  private long runHighlighting() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final Project project = myFixture.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    return TimeoutUtil.measureExecutionTime(() -> ReadAction.run(
      () -> CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), ArrayUtilRt.EMPTY_INT_ARRAY, false)));
  }

  private void doXsltHighlighting() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = testFileName;
    myFixture.testHighlighting(true, false, false, name + ".xsl");
  }
}
