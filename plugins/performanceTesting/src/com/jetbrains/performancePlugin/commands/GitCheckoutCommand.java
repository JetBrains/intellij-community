package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GitCheckoutCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "gitCheckout";

  public GitCheckoutCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    String branchName = extractCommandArgument(PREFIX).replaceAll("\"", "");
    String command = "git checkout " + branchName;
    Project project = context.getProject();
    File projectDir = new File(Objects.requireNonNull(project.getBasePath()));
    String error;
    String output;
    try {
      Process process = Runtime.getRuntime().exec(command, null, projectDir);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        output = reader.lines().collect(Collectors.joining(System.lineSeparator()));
      }
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
        error = reader.lines().collect(Collectors.joining(System.lineSeparator()));
      }
      process.waitFor(1, TimeUnit.MINUTES);
    }
    catch (IOException | InterruptedException ex) {
      actionCallback.reject("Could not run the process with args: " + command + "\n" + ex.getMessage());
      return Promises.toPromise(actionCallback);
    }
    String message = output + System.lineSeparator() + error;

    if (error.contains("Switched to branch")) {
      context.message(message, getLine());
      MessageBusConnection projectConnection = project.getMessageBus().connect();
      projectConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void exitDumbMode() {
          projectConnection.disconnect();
          actionCallback.setDone();
        }
      });
      ApplicationManager.getApplication().invokeLater(() -> {
        FileDocumentManager.getInstance().saveAllDocuments();
        SaveAndSyncHandler.getInstance().refreshOpenFiles();
        VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
      });
    }
    else {
      actionCallback.reject(message);
    }
    return Promises.toPromise(actionCallback);
  }
}
