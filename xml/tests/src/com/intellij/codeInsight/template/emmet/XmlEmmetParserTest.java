// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.nodes.FilterNode;
import com.intellij.codeInsight.template.emmet.nodes.MulOperationNode;
import com.intellij.codeInsight.template.emmet.nodes.UnaryMulOperationNode;
import com.intellij.codeInsight.template.emmet.nodes.ZenCodingNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.emmet.ZenCodingTemplate.findApplicableDefaultGenerator;

public class XmlEmmetParserTest extends LightPlatformCodeInsightTestCase {
  public void testChild() {
    doTest();
  }

  public void testClassAndId() {
    doTest();
  }

  public void testClimbUp1() {
    doTest();
  }

  public void testClimbUp2() {
    doTest();
  }

  public void testCustomAttributes() {
    doTest();
  }

  public void testDefaultAndBooleanAttributes() {
    doTest();
  }

  public void testEmptyAttributes() {
    doTest();
  }

  public void testGrouping() {
    doTest();
  }

  public void testGroups() {
    doTest();
  }

  public void testGroupsWithQuantifier() {
    doTest();
  }

  public void testImplicitTag1() {
    doTest();
  }

  public void testImplicitTag2() {
    doTest();
  }

  public void testImplicitTag3() {
    doTest();
  }

  public void testItemNumbering1() {
    doTest();
  }

  public void testItemNumbering2() {
    doTest();
  }

  public void testMoreAndClimbWithoutRightPart() {
    doTest();
  }

  public void testMultiplication() {
    doTest();
  }

  public void testMultiplicationInGroup() {
    doTest();
  }

  public void testPlusAndAdd() {
    doTest();
  }

  public void testSibling() {
    doTest();
  }

  public void testSimpleGroup() {
    doTest();
  }

  public void testText() {
    doTest();
  }

  public void testAttributeWithPlus() {
    doTest();
  }

  public void testAttributeWithMinus() {
    doTest();
  }

  public void doTest() {
    doTest("html");
  }

  public void doTest(@NotNull String extension) {
    configureByFile(getTestName(true) + "." + extension);
    CustomTemplateCallback callback = new CustomTemplateCallback(getEditor(), getFile());
    ZenCodingGenerator generator = findApplicableDefaultGenerator(callback, false);
    assertNotNull(generator);
    EmmetParser parser = new XmlEmmetParser(new EmmetLexer().lex(getFile().getText()), callback, generator, false);

    final ZenCodingNode parseResult = parser.parse();
    assertNotNull("Cannot parse input data", parseResult);
    assertSameLinesWithFile(getTestDataPath() + getTestName(true) + ".txt", nodeToString(parseResult, 0));
  }

  private static String nodeToString(@NotNull ZenCodingNode zenCodingNode, int level) {
    StringBuilder result = new StringBuilder();
    result.append(StringUtil.repeatSymbol(' ', level * 2));
    result.append(zenCodingNode.toString()).append("\n");
    for (ZenCodingNode node : zenCodingNode.getChildren()) {
      result.append(nodeToString(node, level + 1));
    }
    if (zenCodingNode instanceof FilterNode) {
      result.append(nodeToString(((FilterNode)zenCodingNode).getNode(), level + 1));
    }
    if (zenCodingNode instanceof MulOperationNode) {
      result.append(nodeToString(((MulOperationNode)zenCodingNode).getLeftOperand(), level + 1));
      result.append(StringUtil.repeatSymbol(' ', (level + 1) * 2));
      result.append(((MulOperationNode)zenCodingNode).getRightOperand()).append("\n");
    }
    if (zenCodingNode instanceof UnaryMulOperationNode) {
      result.append(nodeToString(((UnaryMulOperationNode)zenCodingNode).getOperand(), level + 1));
    }
    return result.toString();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath() + "/codeInsight/template/emmet/parser/";
  }
}

