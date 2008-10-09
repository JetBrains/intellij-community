package org.jetbrains.idea.maven.dom;

import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class MavenDependencyScopeConverter extends MavenPropertyResolvingConverter<String> {
  @Override
  public String fromResolvedString(@Nullable @NonNls String s, ConvertContext context) {
    return getVariants(context).contains(s) ? s : null;
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    return Arrays.asList("compile", "provided", "runtime", "test", "system", "import");
  }
}