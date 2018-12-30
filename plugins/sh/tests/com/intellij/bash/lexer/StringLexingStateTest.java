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

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class StringLexingStateTest {
  @Test
  public void testFresh() {
    StringLexingState s = new StringLexingState();

    Assert.assertFalse(s.isInSubshell());
    Assert.assertFalse(s.isInSubstring());
    Assert.assertFalse(s.isSubstringAllowed());
  }

  @Test
  public void testSubshell() {
    StringLexingState s = new StringLexingState();

    s.enterSubshell();

    Assert.assertTrue(s.isSubstringAllowed());
    Assert.assertTrue(s.isInSubshell());

    s.leaveSubshell();

    Assert.assertFalse(s.isInSubshell());
  }

  @Test
  public void testStringInSubshell() {
    StringLexingState s = new StringLexingState();

    Assert.assertFalse(s.isInSubshell());
    Assert.assertFalse(s.isInSubstring());

    s.enterSubshell();
    Assert.assertFalse(s.isInSubstring());

    s.enterString();
    Assert.assertTrue(s.isInSubshell());
    Assert.assertTrue(s.isInSubstring());
    s.leaveString();

    s.enterString();
    Assert.assertTrue(s.isInSubshell());
    Assert.assertTrue(s.isInSubstring());
    s.leaveString();

    Assert.assertFalse(s.isInSubstring());
    s.leaveSubshell();

    Assert.assertFalse(s.isInSubshell());
  }

  @Test
  public void testEnterLeave() {
    StringLexingState s = new StringLexingState();

    Assert.assertFalse(s.isInSubshell());

    s.enterString();
    Assert.assertFalse(s.isInSubstring());

    s.enterSubshell();
    s.enterString();

    Assert.assertTrue(s.isInSubstring());

    s.leaveString();

    Assert.assertFalse(s.isInSubstring());
    Assert.assertTrue(s.isInSubshell());

    s.leaveSubshell();

    Assert.assertFalse(s.isInSubshell());
  }
}