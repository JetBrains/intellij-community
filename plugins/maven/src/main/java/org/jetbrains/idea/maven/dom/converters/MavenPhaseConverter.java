package org.jetbrains.idea.maven.dom.converters;

import org.jetbrains.idea.maven.dom.converters.MavenConstantListConverter;

import java.util.Arrays;
import java.util.List;

public class MavenPhaseConverter extends MavenConstantListConverter {
  private static final List<String> VALUES = Arrays.asList("pre-clean",
                                                           "clean",
                                                           "post-clean",

                                                           "validate",
                                                           "initialize",
                                                           "generate-sources",
                                                           "process-sources",
                                                           "generate-resources",
                                                           "process-resources",
                                                           "compile",
                                                           "process-classes",
                                                           "generate-test-sources",
                                                           "process-test-sources",
                                                           "generate-test-resources",
                                                           "process-test-resources",
                                                           "test-compile",
                                                           "process-test-classes",
                                                           "test",
                                                           "prepare-package",
                                                           "package",
                                                           "pre-integration-test",
                                                           "integration-test",
                                                           "post-integration-test",
                                                           "verify",
                                                           "install",
                                                           "deploy",

                                                           "pre-site",
                                                           "site",
                                                           "post-site",
                                                           "site-deploy");

  protected List<String> getValues() {
    return VALUES;
  }
}