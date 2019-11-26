package com.intellij.laf.macos;

import com.intellij.ide.ui.laf.IntelliJLaf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.laf.macos.MacLafProvider.LAF_NAME;

public class MacIntelliJLaf extends IntelliJLaf {
  @Override
  public String getName() {
    return LAF_NAME;
  }

  @Override
  @NotNull
  protected String getPrefix() {
    return "/macintellijlaf";
  }

  @Override
  @Nullable
  protected String getSystemPrefix() {
    return null;
  }
}
