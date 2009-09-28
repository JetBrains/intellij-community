package org.jetbrains.idea.maven.dom.converters;

import java.util.Arrays;
import java.util.List;

public class MavenDistributionStatusConverter extends MavenConstantListConverter {
  private static final List<String> VALUES
    = Arrays.asList("none", "converted", "partner", "deployed", "verified");

  protected List<String> getValues() {
    return VALUES;
  }
}