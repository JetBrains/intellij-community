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

public class RncAnnotationParsingTest extends AbstractParsingTest {
  public RncAnnotationParsingTest() {
    super("annotations");
  }

  public void testSimple1() {
   doTest(true);
  }

  public void testSimple2() {
   doTest(true);
  }

  public void testNested() {
   doTest(true);
  }

  public void testGrammarLevel() {
   doTest(true);
  }

  public void testFollowing() {
   doTest(true);
  }

  public void testFollowingIncomplete() {
   doTest(true);
  }
}