package org.jetbrains.idea.maven.dom;

import java.util.Arrays;
import java.util.List;

public class MavenRepositoryLayoutConverter extends MavenConstantListConverter {
  private static final List<String> VALUES = Arrays.asList("default", "legacy");

  protected List<String> getValues() {
    return VALUES;
  }
}
