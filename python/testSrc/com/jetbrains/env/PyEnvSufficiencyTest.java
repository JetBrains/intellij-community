// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.junit.Test;

import java.util.*;

import static com.intellij.testFramework.UsefulTestCase.assertEmpty;

public class PyEnvSufficiencyTest extends PyEnvTestCase {
  private static final List<String> BASE_TAGS =
    ImmutableList.<String>builder().add("python3", "django",  "ipython",  "nose", "pytest").build();

  @Test
  public void testSufficiency() {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && SETTINGS.isEnvConfiguration()) {
      checkStaging();

      Set<String> tags = new HashSet<String>();
      List<String> roots = getPythonRoots();
      if (roots.size() == 0) {
        return;         // not on env agent
      }
      for (String root : roots) {
        tags.addAll(loadEnvTags(root));
      }

      List<String> missing = new ArrayList<>();
      for (String tag : necessaryTags()) {
        if (!tags.contains(tag)) {
          missing.add(tag);
        }
      }


      assertEmpty("Agent is missing environments: " + StringUtil.join(missing, ", "), missing);
    }
  }

  private static List<String> necessaryTags() {
    if (SystemInfo.isWindows) {
      return Collections.emptyList();// ImmutableList.<String>builder().addAll(BASE_TAGS).add("iron").build();
    }
    else {
      return ImmutableList.<String>builder().addAll(BASE_TAGS).add("packaging").build();
    }
  }
}
