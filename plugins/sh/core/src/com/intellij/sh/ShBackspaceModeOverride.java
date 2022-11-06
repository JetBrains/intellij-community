// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.codeInsight.editorActions.BackspaceModeOverride;
import com.intellij.codeInsight.editorActions.SmartBackspaceMode;
import org.jetbrains.annotations.NotNull;

final class ShBackspaceModeOverride extends BackspaceModeOverride {
  @NotNull
  @Override
  public SmartBackspaceMode getBackspaceMode(@NotNull SmartBackspaceMode modeFromSettings) {
    return SmartBackspaceMode.INDENT;
  }
}
