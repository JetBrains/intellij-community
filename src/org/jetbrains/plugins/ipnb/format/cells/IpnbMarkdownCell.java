package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

public class IpnbMarkdownCell extends IpnbEditableCell {
  public IpnbMarkdownCell(@NotNull final String[] source) {
    super(source);
  }

  @SuppressWarnings({"CloneDoesntCallSuperClone", "CloneDoesntDeclareCloneNotSupportedException"})
  @Override
  public Object clone() {
    return new IpnbMarkdownCell(getSource());
  }
}
