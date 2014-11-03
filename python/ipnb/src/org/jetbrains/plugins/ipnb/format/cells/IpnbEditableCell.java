package org.jetbrains.plugins.ipnb.format.cells;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public abstract class IpnbEditableCell implements IpnbCell {
  @NotNull private String[] mySource;

  IpnbEditableCell(@NotNull final String[] source) {
    mySource = source;
  }

  @NotNull
  public String[] getSource() {
    return mySource;
  }

  public void setSource(@NotNull final String[] source) {
    mySource = source;
  }

  @NotNull
  public String getSourceAsString() {
    return StringUtil.join(mySource);
  }
}
