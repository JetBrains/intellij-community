// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt;

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnusedNamespaceInspection;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.TimeoutUtil;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.xslt.impl.XsltStuffProvider;

import java.util.Collections;

public class XsltHighlightingTest extends TestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.enableInspections(XsltStuffProvider.INSPECTION_CLASSES);
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList("urn:my"), getTestRootDisposable());
  }

  public void xtestBackwardIncludedVariable() {
    doXsltHighlighting();
  }

  public void testUnknownTemplate() {
    doXsltHighlighting();
  }

  public void testDuplicateTemplate() {
    doXsltHighlighting();
  }

  public void testUnknownMode() {
    doXsltHighlighting();
  }

  public void testUndeclaredParam() {
    doXsltHighlighting();
  }

  public void testMissingParam() {
    doXsltHighlighting();
  }

  public void testUnusedVariable() {
    doXsltHighlighting();
  }

  public void testDuplicateVariable() {
    doXsltHighlighting();
  }

  public void testNonDuplicateVariable() {
    doXsltHighlighting();
  }

  public void testShadowedVariable() {
    doXsltHighlighting();
  }

  public void testShadowedVariable2() {
    doXsltHighlighting();
  }

  public void testValidPatterns() {
    doXsltHighlighting();
  }

  public void testInvalidPattern1() {
    doXsltHighlighting();
  }

  public void testInvalidPattern2() {
    doXsltHighlighting();
  }

  public void testInvalidPattern3() {
    doXsltHighlighting();
  }

  public void testInvalidPattern4() {
    doXsltHighlighting();
  }

  public void testInvalidPattern5() {
    doXsltHighlighting();
  }

  public void testInvalidPattern6() {
    doXsltHighlighting();
  }

  public void testEmptyExpression() {
    doXsltHighlighting();
  }

  public void testEmptyAVT() {
    doXsltHighlighting();
  }

  public void testInvalidSingleClosingBrace() {
    doXsltHighlighting();
  }

  public void testEscapedXPathString() {
    doXsltHighlighting();
  }

  public void testXsltFreeze() {
    doXsltHighlighting();
  }

  public void testTemplateWithPrefix() {
    myFixture.enableInspections(XmlUnusedNamespaceInspection.class);
    doXsltHighlighting();
  }

  public void xtestPerformance() {
    myFixture.configureByFile(getTestFileName() + ".xsl");
    final long l = runHighlighting();
    assertTrue("Highlighting took " + l + "ms", l < 6000);
  }

  private long runHighlighting() {
    final Project project = myFixture.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    return TimeoutUtil.measureExecutionTime(() -> ReadAction.run(
      () -> CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), ArrayUtilRt.EMPTY_INT_ARRAY, false)));
  }

  private void doXsltHighlighting() {
    final String name = getTestFileName();
    myFixture.testHighlighting(true, false, false, name + ".xsl");
  }

  @Override
  protected String getSubPath() {
    return "xslt/highlighting";
  }
}
