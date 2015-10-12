package org.jetbrains.plugins.ipnb.format.cells;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class IpnbEditableCell implements IpnbCell {
  @NotNull private List<String> mySource;

  IpnbEditableCell(@NotNull final List<String> source) {
    mySource = source;
  }

  @NotNull
  public List<String> getSource() {
    return mySource;
  }

  public void setSource(@NotNull final List<String> source) {
    mySource = source;
  }

  @NotNull
  public String getSourceAsString() {
    return StringUtil.join(mySource, "");
  }
}
