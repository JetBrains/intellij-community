/*
 * Copyright (c) Joachim Ansorg, mail@ansorg-it.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.bash.lexer;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 *
 */
public class BashHighlightingLexerTest extends LightCodeInsightFixtureTestCase {
  public void testLexerHighlighting() {
    //test #398, which had a broken lexer which broke the file highlighting with errors after new text was entered

    myFixture.configureByText("a.sh", "$(<caret>)");

    myFixture.type("$");
    myFixture.type("{");
    myFixture.type("1");
    myFixture.type("}"); //typing these characters resulted in lexer exceptions all over the place
  }
}
