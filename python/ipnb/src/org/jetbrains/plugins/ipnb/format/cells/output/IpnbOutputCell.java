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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IpnbOutputCell cell = (IpnbOutputCell)o;

    if (myPromptNumber != null ? !myPromptNumber.equals(cell.myPromptNumber) : cell.myPromptNumber != null) return false;
    if (myText != null ? !myText.equals(cell.myText) : cell.myText != null) return false;
    if (myMetadata != null ? !myMetadata.equals(cell.myMetadata) : cell.myMetadata != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPromptNumber != null ? myPromptNumber.hashCode() : 0;
    result = 31 * result + (myText != null ? myText.hashCode() : 0);
    result = 31 * result + (myMetadata != null ? myMetadata.hashCode() : 0);
    return result;
  }
}
