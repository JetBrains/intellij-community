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

import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class HeredocLexingStateTest {
  @Test
  public void testInitialState() {
    HeredocLexingState s = new HeredocLexingState();
    assertTrue(s.isEmpty());

    assertFalse(s.isNextMarker("x"));
    assertFalse(s.isNextMarker("a"));
  }

  @Test(expected = IllegalStateException.class)
  public void testInitialStateAssertion() {
    HeredocLexingState s = new HeredocLexingState();
    assertFalse(s.isExpectingEvaluatingHeredoc());
  }

  @Test
  public void testStateChange() {
    HeredocLexingState s = new HeredocLexingState();

    s.pushMarker("a", false);
    assertTrue(s.isNextMarker("a"));
    assertFalse(s.isNextMarker("x"));

    s.popMarker("a");

    assertFalse(s.isNextMarker("a"));
    assertFalse(s.isNextMarker("x"));
  }

  @Test
  public void testEvaluatingHeredoc() {
    HeredocLexingState s = new HeredocLexingState();

    s.pushMarker("\"a\"", false);
    assertFalse(s.isExpectingEvaluatingHeredoc());
    s.popMarker("a");

    s.pushMarker("a", false);
    assertTrue(s.isExpectingEvaluatingHeredoc());
    s.popMarker("a");
  }

  @Test(expected = java.lang.IllegalStateException.class)
  public void testInvalidStateChange() {
    HeredocLexingState s = new HeredocLexingState();

    s.pushMarker("a", false);
    s.pushMarker("b", false);

    s.popMarker("x");
  }

  @Test(expected = java.lang.IllegalStateException.class)
  public void testInvalidStateWrongOrder() {
    HeredocLexingState s = new HeredocLexingState();

    s.pushMarker("a", false);
    s.pushMarker("b", false);

    s.popMarker("b");
    s.popMarker("a");
  }
}