package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class IpnbMarkdownCell extends IpnbEditableCell {
  public IpnbMarkdownCell(@NotNull final List<String> source, @NotNull Map<String, Object> metadata) {
    super(source, metadata);
  }

  @SuppressWarnings({"CloneDoesntCallSuperClone"})
  @Override
  public Object clone() {
    return new IpnbMarkdownCell(getSource(), getMetadata());
  }
}
