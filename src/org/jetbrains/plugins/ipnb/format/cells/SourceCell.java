package org.jetbrains.plugins.ipnb.format.cells;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public abstract class SourceCell implements IpnbCell{
  @NotNull private final String[] mySource;

  SourceCell(@NotNull final String[] source) {
    mySource = source;
  }

  @NotNull
  public String[] getSource() {
    return mySource;
  }

  @NotNull
  public String getSourceAsString() {
    return StringUtil.join(mySource, "\n");
  }
}
