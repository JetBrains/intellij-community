package org.jetbrains.plugins.ipnb.format.cells.output;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;

import java.util.List;
import java.util.Map;

public class IpnbOutputCell implements IpnbCell {
  @Nullable protected final Integer myPromptNumber;
  @Nullable private final List<String> myText;
  @Nullable private final Map<String, Object> myMetadata;

  public IpnbOutputCell(@Nullable final List<String> text, @Nullable final Integer promptNumber, @Nullable Map<String, Object> metadata) {
    myText = text;
    myPromptNumber = promptNumber;
    myMetadata = metadata;
  }

  @Nullable
  public List<String> getText() {
    return myText;
  }

  @Nullable
  public String getSourceAsString() {
    return myText == null ? null : StringUtil.join(myText, "\n");
  }

  @Nullable
  public Integer getPromptNumber() {
    return myPromptNumber;
  }

  @Nullable
  public Map<String, Object> getMetadata() {
    return myMetadata;
  }
}
