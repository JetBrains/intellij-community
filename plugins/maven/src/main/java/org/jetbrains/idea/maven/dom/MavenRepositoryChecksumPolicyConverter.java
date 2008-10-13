package org.jetbrains.idea.maven.dom;

import java.util.Arrays;
import java.util.List;

public class MavenRepositoryChecksumPolicyConverter extends MavenConstantListConverter {
  private static final List<String> VALUES = Arrays.asList("ignore",  "fail", "warn");

  protected List<String> getValues() {
    return VALUES;
  }
}