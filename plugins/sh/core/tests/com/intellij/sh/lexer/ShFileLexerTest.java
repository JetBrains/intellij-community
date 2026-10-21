// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixture;
import com.intellij.sh.ShTestData;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixtures;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixtureKt.lexerFixture;

@TestFixtures
public class ShFileLexerTest {
  private final TestFixture<LexerTestFixture> lexer =
    lexerFixture(ShTestData.getTestDataPath() + "/lexer", () -> new ShLexer());
  private String testName;

  @Test public void testFirst() { doFileTest("sh"); }
  @Test public void testHello() { doFileTest("sh"); }
  @Test public void testExprs() { doFileTest("sh"); }
  @Test public void testCase() { doFileTest("sh"); }
  @Test public void testFor() { doFileTest("sh"); }
  @Test public void testIf() { doFileTest("sh"); }
  @Test public void testHeredoc() { doFileTest("sh"); }
  @Test public void testTrap() { doFileTest("sh"); }
  @Test public void testTrap2() { doFileTest("sh"); }
  @Test public void testLet() { doFileTest("sh"); }
  @Test public void testParams() { doFileTest("sh"); }
  @Test public void testSelect() { doFileTest("sh"); }
  @Test public void testBinaryData() { doFileTest("sh"); }
  @Test public void testParamExpansionSub() { doFileTest("sh"); }
  @Test public void testRegex1() { doFileTest("sh"); }
  @Test public void testRegex2() { doFileTest("sh"); }
  @Test public void testStrings() { doFileTest("sh"); }
  @Test public void testParamExpansionEscape() { doFileTest("sh"); } // IDEA-219928
  @Test public void testProcessSubstitution() { doFileTest("sh"); } // IDEA-220072
  @Test public void testTest() { doFileTest("sh"); } // IDEA-244312
  @Test public void testShouldBeFixed() { doFileTest("sh"); }
  @Test public void testIdea263122() { doFileTest("sh"); } // IDEA-263122
  @Test public void testIdea244342() { doFileTest("sh"); } // IDEA-244342
  @Test public void testIdea280499() { doFileTest("sh"); } // IDEA-280499
  @Test public void testIdea289121() { doFileTest("sh"); } // IDEA-289121
  @Test public void testIdea275872() { doFileTest("sh"); } // IDEA-275872
  @Test public void testIdea278953() { doFileTest("sh"); } // IDEA-278953

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = testInfo.getTestMethod().orElseThrow().getName();
  }

  private void doFileTest(@NotNull String fileExt) {
    LexerTestFixture fixture = lexer.get();
    String text = fixture.loadTestDataFile("." + fileExt);
    fixture.doTest(text);
    collectZeroStateStatistics(text);
  }

  private void collectZeroStateStatistics(String text) {
    Lexer lexer = this.lexer.get().createLexer();
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
      System.out.println("Average segment size in test " + testName + ": " + averageSize);
      System.out.println("Segments count: " + segments.size());
      System.out.println("Max rows in segment: " + maxSize);
      System.out.println();
    }
  }
}
