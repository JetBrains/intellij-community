package org.jetbrains.plugins.ipnb.format.cells.output;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CellOutput {
  @Nullable private final String[] myText;

  public CellOutput(@Nullable final String[] text) {
    myText = text;
  }

  @Nullable
  public String[] getText() {
    return myText;
  }

  @Nullable
  public String getSourceAsString() {
    return myText == null ? null : StringUtil.join(myText, "\n");
  }
}
