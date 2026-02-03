// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.openapi.ui.playback.PlaybackCommand;
import org.jetbrains.annotations.NotNull;

public interface CreateCommand {
  @NotNull
  PlaybackCommand invoke(@NotNull String command, int line);
}
