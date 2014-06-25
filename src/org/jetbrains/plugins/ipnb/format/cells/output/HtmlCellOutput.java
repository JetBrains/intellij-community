package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

public class HtmlCellOutput extends CellOutput {
  @NotNull private final String[] myHtml;

  public HtmlCellOutput(@NotNull final String[] html, String[] text) {
    super(text);
    myHtml = html;
  }

  @NotNull
  public String[] getHtmls() {
    return myHtml;
  }
}
