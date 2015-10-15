package org.jetbrains.plugins.ipnb.format.cells.output;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;

import java.util.List;

public class IpnbOutputCell implements IpnbCell {
  @Nullable protected final Integer myPromptNumber;
  @Nullable private final List<String>myText;

  public IpnbOutputCell(@Nullable final List<String> text, @Nullable final Integer promptNumber) {
    myText = text;
    myPromptNumber = promptNumber;
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
}
