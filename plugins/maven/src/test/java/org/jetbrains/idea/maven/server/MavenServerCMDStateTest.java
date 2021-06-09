// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.junit.Assert;
import org.junit.Test;

public class MavenServerCMDStateTest {

  @Test
  public void testMaxXmxStringValueSecondNull() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx768m", null);
    Assert.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueFirstNull() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue(null, "-Xms768m");
    Assert.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueBothNull() {
    Assert.assertNull(MavenServerCMDState.getMaxXmxStringValue(null, null));
  }

  @Test
  public void testMaxXmxStringValueEq() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx768m", "-Xms768m");
    Assert.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondLess() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx768m", "-Xms124m");
    Assert.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondLessOtherUnit() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx1g", "-Xms1024k");
    Assert.assertEquals("-Xmx1g", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondGreat() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx768m", "-Xms1024m");
    Assert.assertEquals("-Xmx1024m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondGreatOtherUnit() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx1m", "-Xms1025k");
    Assert.assertEquals("-Xmx1025k", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondGreatOtherUnitGigabyte() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx1m", "-Xms1G");
    Assert.assertEquals("-Xmx1g", xmxProperty);
  }
}