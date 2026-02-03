// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.plugins.CreateAllServicesAndExtensionsActionKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class CreateAllServicesAndExtensionsCommand extends AbstractCallbackBasedCommand {

  public static final @NonNls String PREFIX = CMD_PREFIX + "CreateAllServicesAndExtensions";

  public CreateAllServicesAndExtensionsCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected void execute(@NotNull ActionCallback callback,
                         @NotNull PlaybackContext context) {
    if (ApplicationManager.getApplication().isInternal()) {
      try {
        CreateAllServicesAndExtensionsActionKt.performAction();
        callback.setDone();
      }
      catch (Exception e) {
        callback.reject(e.getMessage());
      }
    }
    else {
      callback.reject("Internal mode is required for the '" + CreateAllServicesAndExtensionsActionKt.ACTION_ID + "' command." +
                      "Please see https://plugins.jetbrains.com/docs/intellij/enabling-internal.html");
    }
  }
}
