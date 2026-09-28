// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MavenServerCMDStateTest {

  @Test
  public void testMaxXmxStringValueSecondNull() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx768m", null);
    Assertions.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueFirstNull() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue(null, "-Xms768m");
    Assertions.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueBothNull() {
    Assertions.assertNull(MavenServerCMDState.getMaxXmxStringValue(null, null));
  }

  @Test
  public void testMaxXmxStringValueEq() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx768m", "-Xms768m");
    Assertions.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondLess() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx768m", "-Xms124m");
    Assertions.assertEquals("-Xmx768m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondLessOtherUnit() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx1g", "-Xms1024k");
    Assertions.assertEquals("-Xmx1g", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondGreat() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx768m", "-Xms1024m");
    Assertions.assertEquals("-Xmx1024m", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondGreatOtherUnit() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx1m", "-Xms1025k");
    Assertions.assertEquals("-Xmx1025k", xmxProperty);
  }

  @Test
  public void testMaxXmxStringValueSecondGreatOtherUnitGigabyte() {
    String xmxProperty = MavenServerCMDState.getMaxXmxStringValue("-Xmx1m", "-Xms1G");
    Assertions.assertEquals("-Xmx1g", xmxProperty);
  }
}