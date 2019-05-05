package com.intellij.bash;

import com.intellij.codeInsight.editorActions.BackspaceModeOverride;
import com.intellij.codeInsight.editorActions.SmartBackspaceMode;
import org.jetbrains.annotations.NotNull;

public class ShBackspaceModeOverride extends BackspaceModeOverride {
  @NotNull
  @Override
  public SmartBackspaceMode getBackspaceMode(@NotNull SmartBackspaceMode modeFromSettings) {
    return SmartBackspaceMode.INDENT;
  }
}
