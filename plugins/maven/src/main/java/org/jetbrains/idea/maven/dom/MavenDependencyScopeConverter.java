package org.jetbrains.idea.maven.dom;

import java.util.Arrays;
import java.util.List;

public class MavenDependencyScopeConverter extends MavenConstantListConverter {
  private static final List<String> VALUES = Arrays.asList(
    "compile", "provided", "runtime", "test", "system", "import");

  public MavenDependencyScopeConverter() {
    super(false);
  }

  protected List<String> getValues() {
    return VALUES;
  }
}