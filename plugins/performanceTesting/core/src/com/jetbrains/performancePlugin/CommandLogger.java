package com.jetbrains.performancePlugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.PlaybackRunner.StatusCallback;
import org.jetbrains.annotations.Nullable;

public class CommandLogger implements StatusCallback {
  private static final Logger LOG = Logger.getInstance(CommandLogger.class);
  @Override
  public void message(@Nullable PlaybackContext context, String text, Type type) {
    if (type == Type.error) {
      LOG.error(text);
    }
    else {
      LOG.info(text);
    }
  }
}
