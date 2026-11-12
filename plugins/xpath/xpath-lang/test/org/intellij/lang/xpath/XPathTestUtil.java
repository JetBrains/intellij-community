// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath;

import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.TestInfo;

/**
 * Gives the test data path and the test file name for the XPath and XSLT tests.
 */
public final class XPathTestUtil {
  private XPathTestUtil() {
  }

  public static @NotNull String getTestDataPath(@NotNull String subPath) {
    // path logic taken from intellij.regexp tests
    final String def = PluginPathManager.getPluginHomePath("xpath") + "/xpath-lang/testData";
    return System.getProperty("idea.xpath.testdata-path", def) + "/" + subPath;
  }

  /**
   * Returns the name of the test method without the {@code test} prefix, with a lowercase first letter.
   */
  public static @NotNull String getTestFileName(@NotNull TestInfo testInfo) {
    final String s = testInfo.getTestMethod().orElseThrow().getName().substring("test".length());
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }
}
