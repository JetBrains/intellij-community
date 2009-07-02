package org.jetbrains.idea.maven.dom.converters;

import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.MavenConstantListConverter;

import java.util.Collections;
import java.util.List;

public class MavenModelVersionConverter extends MavenConstantListConverter {
  private static final String VERSION = "4.0.0";
  private static final List<String> VALUES = Collections.singletonList(VERSION);

  protected List<String> getValues() {
    return VALUES;
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return "Unsupported model version. Only version " + VERSION + " is supported.";
  }
}
