package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class StopPowerSave extends AbstractCommand implements Disposable {

  public static final String PREFIX = CMD_PREFIX + "stopPowerSave";

  public StopPowerSave(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    final MessageBusConnection busConnection = context.getProject().getMessageBus().connect();
    busConnection.subscribe(PowerSaveMode.TOPIC, () -> actionCallback.setDone());

    PowerSaveMode.setEnabled(false);

    return Promises.toPromise(actionCallback);
  }

  @Override
  public void dispose() {
  }
}
