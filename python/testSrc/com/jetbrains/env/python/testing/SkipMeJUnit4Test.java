// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.testing;

import com.jetbrains.env.PyEnvTestCase;
import org.junit.Assume;
import org.junit.Test;

public class SkipMeJUnit4Test extends PyEnvTestCase {

  @Test
  public void testOk() {

  }

  @Test
  public void testOk2() {
    Assume.assumeTrue("ok", true);
  }

  @Test
  public void testSkip() {
    Assume.assumeTrue("Skip me", false);
  }
}
