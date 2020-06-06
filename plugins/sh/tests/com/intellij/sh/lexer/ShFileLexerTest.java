// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ShFileLexerTest extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new ShLexer();
  }

  @Override
  protected String getDirPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/testData/lexer";
  }

  @NotNull
  @Override
  protected String getPathToTestDataFile(String extension) {
    return getDirPath() + "/" + getTestName(true) + extension;
  }

  public void testFirst()                { doFileTest("sh"); }
  public void testHello()                { doFileTest("sh"); }
  public void testExprs()                { doFileTest("sh"); }
  public void testCase()                 { doFileTest("sh"); }
  public void testFor()                  { doFileTest("sh"); }
  public void testIf()                   { doFileTest("sh"); }
  public void testHeredoc()              { doFileTest("sh"); }
  public void testTrap()                 { doFileTest("sh"); }
  public void testTrap2()                { doFileTest("sh"); }
  public void testLet()                  { doFileTest("sh"); }
  public void testParams()               { doFileTest("sh"); }
  public void testSelect()               { doFileTest("sh"); }
  public void testBinaryData()           { doFileTest("sh"); }
  public void testParamExpansionSub()    { doFileTest("sh"); }
  public void testRegex1()               { doFileTest("sh"); }
  public void testRegex2()               { doFileTest("sh"); }
  public void testStrings()              { doFileTest("sh"); }
  public void testParamExpansionEscape() { doFileTest("sh"); } // IDEA-219928
  public void testProcessSubstitution()  { doFileTest("sh"); } // IDEA-220072
  public void testShouldBeFixed()        { doFileTest("sh"); }
  public void testConditional()          { doFileTest("sh"); }

  @Override
  protected void doFileTest(String fileExt) {
    super.doFileTest(fileExt);
    String text = loadTestDataFile("." + fileExt);
    checkCorrectRestart(text);
    collectZeroStateStatistics(text);
  }

  private void collectZeroStateStatistics(String text) {
    Lexer lexer = createLexer();
    lexer.start(text);

    List<Integer> segments = new ArrayList<>();
    boolean rowWithZeroState = false;
    boolean segmentStart = false;
    int segmentSize = 0;
    while (lexer.getTokenType() != null) {

      if (lexer.getState() == 0) {
        rowWithZeroState = true;
        if (segmentStart) {
          segments.add(segmentSize);
          segmentSize = 0;
          segmentStart = false;
        }
      }

      if (lexer.getTokenType().toString().equals("\\n")) {
        if (!rowWithZeroState) {
          segmentSize++;
          segmentStart = true;
        }
        rowWithZeroState = false;
      }
      lexer.advance();
    }
    if (segmentStart) {
      segments.add(segmentSize);
    }
    double averageSize = segments.stream().mapToInt(Integer::intValue).average().orElse(0);
    double maxSize = segments.stream().mapToInt(Integer::intValue).max().orElse(0);

    if (segments.size() > 0) {
      System.out.println("Average segment size in test " + getName() + ": " + averageSize);
      System.out.println("Segments count: " + segments.size());
      System.out.println("Max rows in segment: " + maxSize);
      System.out.println();
    }
  }
}
