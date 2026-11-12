/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath;

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixtures;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixtureKt.lexerFixture;

@TestFixtures
public class XPathLexerTest {
  private final TestFixture<LexerTestFixture> lexer = lexerFixture(XPathTestUtil.getTestDataPath("xpath/parsing/lexer"), () -> XPathLexer.create(false));

  @Test
  public void testAttributeAxis() {
    lexer.get().doTest("attribute::*");
  }

  @Test
  public void testBadAxis() {
    lexer.get().doTest("something::*");
  }

  @Test
  public void testAttributeNodeType() {
    lexer.get().doTest("attribute()");
  }

  @Test
  public void testElementNodeType() {
    lexer.get().doTest("element()");
  }

  @Test
  public void testAttributeNCName() {
    lexer.get().doTest("attribute/*");
  }

  @Test
  public void testElementNCName() {
    lexer.get().doTest("element/*");
  }

  @Test
  public void testPrefixedNameAnd() {
    lexer.get().doTest("child::xsd:element and contains('a', 'a')");
  }
}