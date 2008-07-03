package org.jetbrains.idea.maven.dom;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class MavenPluginPhaseConverter extends ResolvingConverter<String> {
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return getVariants(context).contains(s) ? s : null;
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    return Arrays.asList("pre-clean",
                         "clean",
                         "post-clean",

                         "validate",
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
  }
}