package org.jetbrains.idea.maven.dom.converters;

import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.MavenConstantListConverter;

import java.util.Arrays;
import java.util.List;

public class MavenRepositoryUpdatePolicyConverter extends MavenConstantListConverter {
  private static final String INTERVAL = "interval:";
  private static final List<String> VALUES = Arrays.asList("always", "daily", INTERVAL, "never");

  @Override
  public String fromResolvedString(@Nullable @NonNls String s, ConvertContext context) {
    if (s != null && s.startsWith(INTERVAL)) return s;
    return super.fromResolvedString(s, context);
  }

  protected List<String> getValues() {
    return VALUES;
  }
}