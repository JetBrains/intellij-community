package com.jetbrains.performanceScripts.lang;

import org.jetbrains.annotations.NonNls;

public final class TestUtil {

  private TestUtil() {
  }

  @NonNls
  public static String getDataSubPath(@NonNls String theme) {
    return "/plugins/performanceTesting/scripts-ui/testData/" + theme;
  }
}
