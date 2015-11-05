package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IpnbMarkdownCell extends IpnbEditableCell {
  public IpnbMarkdownCell(@NotNull final List<String> source) {
    super(source);
  }

  @SuppressWarnings({"CloneDoesntCallSuperClone", "CloneDoesntDeclareCloneNotSupportedException"})
  @Override
  public Object clone() {
    return new IpnbMarkdownCell(getSource());
  }
}
