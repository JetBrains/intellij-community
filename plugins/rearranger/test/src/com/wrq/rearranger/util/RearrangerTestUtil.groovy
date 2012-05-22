package com.wrq.rearranger.util

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 5/22/12 11:11 AM
 */
public class RearrangerTestUtil {

  private RearrangerTestUtil() {
  }

  public static void setIf(@NotNull RearrangerTestDsl dslProperty, map, rulePropertyName, rule) {
    if (map.containsKey(dslProperty.value)) {
      rule."$rulePropertyName" = map[dslProperty.value]
    }
  }
}
