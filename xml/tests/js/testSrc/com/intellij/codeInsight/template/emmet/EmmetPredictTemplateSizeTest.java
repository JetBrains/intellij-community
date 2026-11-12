// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.nodes.ZenCodingNode;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.emmet.ZenCodingTemplate.findApplicableDefaultGenerator;

public class EmmetPredictTemplateSizeTest extends BasePlatformTestCase {
  public void testMulOperation() {
    doTest("div*5", 55);
  }

  public void testLorem() {
    doTest("lorem30", 210);
  }
  
  public void testAddOperation() {
    doTest("div>div+div", 33);
  }
  public void testMoreOperation() {
    doTest("ul>li>span", 31);
  }
  
  public void testMoreAfterMultiplyOperation() {
    doTest("div*2>li", 40);
  }
  
  public void testFilter() {
    doTest("div*2>li|s", 40);
  }

  public void testClimbUpOperation() {
    doTest("div>ul^ul", 29);
  }

  public void testTemplateWithAttributes() {
    doTest("div[attr=val].class", 36);
  }

  private void doTest(@NotNull String content, int expectedLength) {
    myFixture.configureByText(HtmlFileType.INSTANCE, content + "<caret>");
    CustomTemplateCallback callback = new CustomTemplateCallback(myFixture.getEditor(), myFixture.getFile());
    ZenCodingGenerator generator = findApplicableDefaultGenerator(callback, false);
    assertNotNull(generator);
    
    EmmetParser parser = new XmlEmmetParser(new EmmetLexer().lex(content), callback, generator, false);
    ZenCodingNode zenCodingNode = parser.parse();
    assertNotNull(zenCodingNode);
    assertEquals(expectedLength, zenCodingNode.getApproximateOutputLength(callback));
  }
}
