package org.jetbrains.plugins.ipnb.format.cells;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public abstract class IpnbEditableCell implements IpnbCell {
  @NotNull private List<String> mySource;
  @Nullable Map<String, Object> myMetadata;

  IpnbEditableCell(@NotNull final List<String> source, @Nullable Map<String, Object> metadata) {
    mySource = source;
    myMetadata = metadata;
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

  @Nullable
  public Map<String, Object> getMetadata() {
    return myMetadata;
  }
}
