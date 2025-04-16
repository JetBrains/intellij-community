// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.ide.plugins.DynamicPluginVetoer;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackCommand;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlaybackRunnerExtended extends PlaybackRunner {
  public static final String NOTIFICATION_GROUP = "PerformancePlugin";

  private Project myProject;
  private boolean myStopped;

  public PlaybackRunnerExtended(String script, StatusCallback callback, @NotNull Project project) {
    super(script, callback, Registry.is("performance.plugin.playback.runner.useDirectActionCall", false), false, true);
    setProject(project);

    PlaybackRunnerExtendedPluginUnloadVetoer.activeExecutionCount.incrementAndGet();
    Disposer.register(onStop, () -> {
      PlaybackRunnerExtendedPluginUnloadVetoer.activeExecutionCount.decrementAndGet();
    });
  }

  @Override
  public void setProject(@Nullable Project project) {
    myProject = project;

    if (project != null && !project.isDefault() && !project.isDisposed()) {
      Disposer.register(project, () -> {
        if (project == myProject) {
          Disposer.dispose(onStop);
        }
      });
    }
  }

  @Override
  public @Nullable Project getProject() {
    return myProject;
  }

  @Override
  protected void onStop() {
    super.onStop();
    myStopped = true;
  }

  @Override
  protected @Nullable PlaybackCommand createCommand(@NotNull String _command, int line, @NotNull File scriptDir) {
    String command = _command.replaceAll("[\r\n]+$", "");
    String[] cmdline = command.split("\\s+");
    PlaybackCommand playbackCommand = null;
    if (cmdline.length > 0) {
      String commandName = cmdline[0];
      CreateCommand createCommand = CommandProvider.findCommandCreator(commandName);
      if (createCommand != null) {
        playbackCommand = createCommand.invoke(command, line);
        if (playbackCommand instanceof AbstractCommand) {
          ((AbstractCommand)playbackCommand).setScriptDir(scriptDir);
        }
      }
    }
    if (playbackCommand == null) {
      playbackCommand = super.createCommand(command, line, scriptDir);
    }
    return playbackCommand;
  }

  @Override
  public CompletableFuture<?> run() {
    if (myStopped) {
      throw new IllegalStateException("PlaybackRunnerExtended can be run only once.");
    }
    CompletableFuture<?> callback = super.run();
    RunCallbackHandler.applyPatchesToCommandCallback(myProject, callback);
    return callback;
  }

  static class PlaybackRunnerExtendedPluginUnloadVetoer implements DynamicPluginVetoer {
    static final AtomicInteger activeExecutionCount = new AtomicInteger();

    @Override
    public @Nls @Nullable String vetoPluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor) {
      if (activeExecutionCount.get() > 0 &&
          PluginId.getId("com.jetbrains.performancePlugin").equals(pluginDescriptor.getPluginId())) {
        return "Cannot unload plugin during playback execution";
      }
      return null;
    }
  }
}
