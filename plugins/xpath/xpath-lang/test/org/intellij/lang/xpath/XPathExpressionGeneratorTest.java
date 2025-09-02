// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.intellij.plugins.xpathView.XPathExpressionGenerator;
import org.jetbrains.annotations.NotNull;

public class XPathExpressionGeneratorTest extends TestBase {

  public void testBasic1() {
    assertXPath("/stopWord/word/foo", "/stopWord/word[4]/foo");
  }

  public void testBasic2() {
    assertXPath("/stopWord/word/@attribute", "/stopWord/word[4]/@attribute");
  }

  public void testManySiblingsPerformance() {
    Benchmark.newBenchmark("Many siblings", () -> {
        assertXPath("/stopWord/word", "/stopWord/word[2359]");
      })
      .start();
  }

  public void testName() {
    assertXPath("/stopWord/word", "/stopWord/word[@name='vergn']");
  }

  public void testDuplicatedName() {
    assertXPath("/stopWord/word", "/stopWord/word[4]");
  }

  private void assertXPath(@NotNull String expected, @NotNull String expectedUnique) {
    myFixture.configureByFile(getTestName(true) + ".xml");
    var tag = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()),
                                          XmlTag.class, XmlAttribute.class);
    assertEquals(expected, XPathExpressionGenerator.getPath(tag, null));
    assertEquals(expectedUnique, XPathExpressionGenerator.getUniquePath(tag, null));
  }

  @Override
  protected String getSubPath() {
    return "xpath/generator";
  }
}
