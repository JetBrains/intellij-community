package com.intellij.bash.lexer;

import org.junit.Assert;
import org.junit.Test;

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