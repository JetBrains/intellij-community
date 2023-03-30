// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.testFramework.LexerTestCase;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class XmlLexerTest extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new XmlLexer();
  }

  @Override
  protected String getDirPath() {
    return "/xml/tests/testData/lexer/xml";
  }

  @Override
  protected @NotNull String getPathToTestDataFile(String extension) {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/" + getDirPath() + "/" + getTestName(true) + extension;
  }


  public void testPerformance1() throws IOException {
    doTestPerformance("pallada.xml", 200);
  }

  public void testPerformance2() throws IOException {
    doTestPerformance("performance2.xml", 400);
  }

  private static void doTestPerformance(String fileName, int expectedMs) throws IOException {
    final String text = ParsingTestCase.loadFileDefault(
      PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/psi/xml",
      fileName);
    final XmlLexer lexer = new XmlLexer();
    final FilterLexer filterLexer = new FilterLexer(new XmlLexer(),
                                                    new FilterLexer.SetFilter(new XMLParserDefinition().getWhitespaceTokens()));

    PlatformTestUtil.startPerformanceTest("XML Lexer Performance on " + fileName, expectedMs, () -> {
      for (int i = 0; i < 10; i++) {
        doLex(lexer, text);
        doLex(filterLexer, text);
      }
    }).assertTiming();
  }

  private static void doLex(Lexer lexer, final String text) {
    lexer.start(text);
    long time = System.nanoTime();
    int count = 0;
    while (lexer.getTokenType() != null) {
      lexer.advance();
      count++;
    }
    LOG.debug("Plain lexing took " + (System.nanoTime() - time) + "ns. Lexemes count:" + count);
  }
}
