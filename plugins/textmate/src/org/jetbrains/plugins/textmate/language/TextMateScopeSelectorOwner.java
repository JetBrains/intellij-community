package org.jetbrains.plugins.textmate.language;

import org.jetbrains.annotations.NotNull;

public interface TextMateScopeSelectorOwner {
  @NotNull
  CharSequence getScopeSelector();
}
