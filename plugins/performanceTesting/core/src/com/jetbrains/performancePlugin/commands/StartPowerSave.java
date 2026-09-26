// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class StartPowerSave extends AbstractCommand implements Disposable {

  public static final String PREFIX = CMD_PREFIX + "startPowerSave";

  public StartPowerSave(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    final MessageBusConnection busConnection = context.getProject().getMessageBus().connect();
    busConnection.subscribe(PowerSaveMode.TOPIC, () -> actionCallback.setDone());

    ApplicationManager.getApplication().invokeAndWait(() -> {
      PowerSaveMode.setEnabled(true);
    });


    return Promises.toPromise(actionCallback);
  }

  @Override
  public void dispose() {
  }
}
