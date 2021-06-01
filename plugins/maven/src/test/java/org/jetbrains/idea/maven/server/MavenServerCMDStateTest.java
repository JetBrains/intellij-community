// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.junit.Assert;
import org.junit.Test;

public class MavenServerCMDStateTest {

  @Test
  public void testGetDefaultXmxProperty() {
    String xmxProperty = MavenServerCMDState.getDefaultXmxProperty("-Xmx768m", null);
    Assert.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testGetDefaultXmxPropertyEq() {
    String xmxProperty = MavenServerCMDState.getDefaultXmxProperty("-Xmx768m", "-Xms768m");
    Assert.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testGetDefaultXmxPropertyLess() {
    String xmxProperty = MavenServerCMDState.getDefaultXmxProperty("-Xmx768m", "-Xms124m");
    Assert.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testGetDefaultXmxPropertyLess1() {
    String xmxProperty = MavenServerCMDState.getDefaultXmxProperty("-Xmx1g", "-Xms1024k");
    Assert.assertEquals("-Xmx1g", xmxProperty);
  }

  @Test
  public void testGetDefaultXmxPropertyGreat() {
    String xmxProperty = MavenServerCMDState.getDefaultXmxProperty("-Xmx768m", "-Xms1024m");
    Assert.assertEquals("-Xmx1024m", xmxProperty);
  }

  @Test
  public void testGetDefaultXmxPropertyGreat1() {
    String xmxProperty = MavenServerCMDState.getDefaultXmxProperty("-Xmx1m", "-Xms1025k");
    Assert.assertEquals("-Xmx1025k", xmxProperty);
  }

  @Test
  public void testGetDefaultXmxPropertyGreat2() {
    String xmxProperty = MavenServerCMDState.getDefaultXmxProperty("-Xmx1m", "-Xms1G");
    Assert.assertEquals("-Xmx1g", xmxProperty);
  }

  @Test(expected = AssertionError.class)
  public void testGetDefaultXmxPropertyError() {
    MavenServerCMDState.getDefaultXmxProperty("-Xms1m", "-Xms1G");
  }

  @Test(expected = AssertionError.class)
  public void testGetDefaultXmxPropertyError2() {
    MavenServerCMDState.getDefaultXmxProperty("-Xmx1m", "-Xmx1G");
  }
}