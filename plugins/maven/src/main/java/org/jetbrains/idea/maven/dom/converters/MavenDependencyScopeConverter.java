package org.jetbrains.idea.maven.dom.converters;

import org.jetbrains.idea.maven.utils.MavenConstants;

import java.util.Arrays;
import java.util.List;

public class MavenDependencyScopeConverter extends MavenConstantListConverter {
  private static final List<String> VALUES = Arrays.asList(
    MavenConstants.SCOPE_COMPILE,
    MavenConstants.SCOPE_PROVIDEED,
    MavenConstants.SCOPE_RUNTIME,
    MavenConstants.SCOPE_TEST,
    MavenConstants.SCOPE_SYSTEM,
    MavenConstants.SCOPE_IMPORT);

  public MavenDependencyScopeConverter() {
    super(false);
  }

  protected List<String> getValues() {
    return VALUES;
  }
}
