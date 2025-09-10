// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.util.lexer.FilterLexer;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.syntax.LexerTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.xml.syntax.XmlSyntaxDefinition;
import com.intellij.xml.syntax.lexer.XmlLexer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class XmlLexerTest extends LexerTestCase {
  @Override
  protected @NotNull Lexer createLexer() {
    return new XmlLexer();
  }

  @Override
  protected @NotNull String getDirPath() {
    return "/xml/tests/testData/lexer/xml";
  }

  @Override
  protected @NotNull String getPathToTestDataFile(@NotNull String extension) {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/" + getDirPath() + "/" + getTestName(true) + extension;
  }

  public void testWrappedEL() {
    doTest("<idea-plugin><name>Remote${ Interpreter}</name></idea-plugin>");
  }

  public void testUnfinishedEL() {
    doTest("<idea-plugin><name>Remote${ Interpreter</name></idea-plugin>");
  }

  public void testUnfinishedStringInDoctype() {
    doTest("<!DOCTYPE faces-config PUBLIC \"-//Sun Microsystems, Inc.//DTD JavaServer Faces Config1.0//EN");
  }

  public void testUnfinishedMarkupDeclarationInDoctype() {
    doTest("<!DOCTYPE schema [ <!ENTITY RelativeURL  \"[^:#/\\?]*(:{0,0}|[#/\\?].*)\">");
  }


  public void testPerformance1() throws IOException {
    doTestPerformance("pallada.xml");
  }

  public void testPerformance2() throws IOException {
    doTestPerformance("performance2.xml");
  }

  private static void doTestPerformance(String fileName) throws IOException {
    final String text = ParsingTestCase.loadFileDefault(
      PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/psi/xml",
      fileName);
    final XmlLexer lexer = new XmlLexer();
    final FilterLexer filterLexer = new com.intellij.platform.syntax.util.lexer.FilterLexer(
      new XmlLexer(),
      new FilterLexer.SetFilter(XmlSyntaxDefinition.INSTANCE.getWHITESPACES())
    );

    Benchmark.newBenchmark("XML Lexer Performance on " + fileName, () -> {
      for (int i = 0; i < 10; i++) {
        doLex(lexer, text);
        doLex(filterLexer, text);
      }
    }).start();
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
