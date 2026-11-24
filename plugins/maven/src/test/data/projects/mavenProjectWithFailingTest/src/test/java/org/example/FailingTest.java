package org.example;

import org.junit.Assert;
import org.junit.Test;

/** Always fails, so that Maven reports a failed `test` mojo. */
public class FailingTest {
  @Test
  public void testThatFails() {
    Assert.assertEquals("expected failure", 1, 2);
  }
}
