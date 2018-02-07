package org.jetbrains.plugins.ruby.ruby.actions.handlers;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public abstract class RunAnythingCommandHandler {
  public static final ExtensionPointName<RunAnythingCommandHandler> EP_NAME =
    ExtensionPointName.create("org.jetbrains.plugins.ruby.runAnythingCommandHandler");

  public abstract boolean isMatched(@NotNull String commandLine);

  public boolean isSilentlyDestroyOnClose() {
    return false;
  }

  public boolean shouldKillProcessSoftly() {
    return true;
  }

  public static boolean isSilentlyDestroyOnClose(@NotNull String commandLine) throws RuntimeException {
    for (RunAnythingCommandHandler handler : EP_NAME.getExtensions()) {
      if (handler.isMatched(commandLine)) {
        return handler.isSilentlyDestroyOnClose();
      }
    }
    throw new RuntimeException();
  }

  public static boolean shouldKillProcessSoftly(@NotNull String commandLine) throws RuntimeException {
    for (RunAnythingCommandHandler handler : EP_NAME.getExtensions()) {
      if (handler.isMatched(commandLine)) {
        return handler.shouldKillProcessSoftly();
      }
    }
    throw new RuntimeException();
  }
}