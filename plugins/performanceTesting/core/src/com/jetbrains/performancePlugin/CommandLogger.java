// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
