/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG;

public class ExternalRncParsingTest extends HighlightingTestBase {
  @Override
  public String getTestDataPath() {
    return "parsing";
  }

  public void testParseSimple() {
    doTestHighlighting("simple.rnc");
  }

  public void testParseEscaped() {
    doTestHighlighting("escaped.rnc");
  }

  public void testParseEscapedBroken1() {
    doTestHighlighting("escaped-broken-1.rnc");
  }

  public void testParseEscapedBroken2() {
    doTestHighlighting("escaped-broken-2.rnc");
  }

  public void testParseEscapedBroken3() {
    doTestHighlighting("escaped-broken-3.rnc");
  }

  public void testParseEscapedBroken4() {
    doTestHighlighting("escaped-broken-4.rnc");
  }

  public void testParseEscapedBroken5() {
    doTestHighlighting("escaped-broken-5.rnc");
  }

  private void doTestHighlighting(String s) {
    doExternalToolHighlighting(s);
  }
}