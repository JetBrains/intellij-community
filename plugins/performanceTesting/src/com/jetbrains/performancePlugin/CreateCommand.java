package com.jetbrains.performancePlugin;

import com.intellij.openapi.ui.playback.PlaybackCommand;
import org.jetbrains.annotations.NotNull;

public interface CreateCommand {
  @NotNull
  PlaybackCommand invoke(@NotNull String command, int line);
}
