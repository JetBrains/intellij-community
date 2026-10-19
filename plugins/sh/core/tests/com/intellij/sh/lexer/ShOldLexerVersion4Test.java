// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixture;
import com.intellij.sh.ShTestData;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixtures;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixtureKt.lexerFixture;

@TestFixtures
public class ShOldLexerVersion4Test {
  private final TestFixture<LexerTestFixture> lexer =
    lexerFixture(ShTestData.getTestDataPath() + "/oldLexer/v4", () -> new ShLexer());

  @Test
  public void testCasePattern() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue469() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testV4Lexing() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue243() {
    lexer.get().doFileTest("sh");
  }
}

