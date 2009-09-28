package org.jetbrains.idea.maven.dom.converters;

import java.util.Arrays;
import java.util.List;

public class MavenPackagingTypeConverter extends MavenConstantListConverter {
  private static final List<String> VALUES
    = Arrays.asList("jar", "pom", "war", "ejb", "ejb-client", "ear");

  public MavenPackagingTypeConverter() {
    super(false);
  }

  protected List<String> getValues() {
    return VALUES;
  }
}