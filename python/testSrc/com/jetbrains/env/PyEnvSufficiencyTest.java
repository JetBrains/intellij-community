package com.jetbrains.env;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PyEnvSufficiencyTest extends PyEnvTestCase {
  private static final List<String> BASE_TAGS =
    ImmutableList.<String>builder().add("python3", "django", "jython", "ipython", "ipython011", "ipython012", "nose", "pytest").build();

  public void testSufficiency() {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && IS_ENV_CONFIGURATION) {
      Set<String> tags = Sets.newHashSet();
      List<String> roots = getPythonRoots();
      if (roots.size() == 0) {
        return;         // not on env agent
      }
      for (String root : roots) {
        tags.addAll(loadEnvTags(root));
      }

      List<String> missing = Lists.newArrayList();
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
      return ImmutableList.<String>builder().addAll(BASE_TAGS).add("iron").build();
    }
    else {
      return ImmutableList.<String>builder().addAll(BASE_TAGS).add("packaging").build();
    }
  }
}
