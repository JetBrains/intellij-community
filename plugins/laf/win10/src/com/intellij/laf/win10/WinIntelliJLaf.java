package com.intellij.laf.win10;

import com.intellij.ide.ui.laf.IntelliJLaf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.laf.win10.WinLafProvider.LAF_NAME;

public class WinIntelliJLaf extends IntelliJLaf {
  @Override
  public String getName() {
    return LAF_NAME;
  }

  @Override
  @NotNull
  protected String getPrefix() {
    return "/win10intellijlaf";
  }

  @Override
  @Nullable
  protected String getSystemPrefix() {
    return null;
  }
}
