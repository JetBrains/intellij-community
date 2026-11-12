// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.intellij.plugins.xpathView.XPathExpressionGenerator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class XPathExpressionGeneratorTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XPathTestUtil.getTestDataPath("xpath/generator"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
  }

  @Test
  public void testBasic1() {
    assertXPath("/stopWord/word/foo", "/stopWord/word[4]/foo");
  }

  @Test
  public void testBasic2() {
    assertXPath("/stopWord/word/@attribute", "/stopWord/word[4]/@attribute");
  }

  @PerformanceUnitTest
  @Test
  public void testManySiblingsPerformance() {
    Benchmark.newBenchmark("Many siblings", () -> {
        assertXPath("/stopWord/word", "/stopWord/word[2359]");
      })
      .runAsStressTest()
      .start();
  }

  @Test
  public void testName() {
    assertXPath("/stopWord/word", "/stopWord/word[@name='vergn']");
  }

  @Test
  public void testDuplicatedName() {
    assertXPath("/stopWord/word", "/stopWord/word[4]");
  }

  private void assertXPath(@NotNull String expected, @NotNull String expectedUnique) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testFileName + ".xml");
    ReadAction.runBlocking(() -> {
      var tag = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()),
                                            XmlTag.class, XmlAttribute.class);
      assertEquals(expected, XPathExpressionGenerator.getPath(tag, null));
      assertEquals(expectedUnique, XPathExpressionGenerator.getUniquePath(tag, null));
    });
  }
}
