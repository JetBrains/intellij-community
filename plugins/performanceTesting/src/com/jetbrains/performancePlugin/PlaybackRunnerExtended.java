package com.jetbrains.performancePlugin;

import com.intellij.ide.plugins.CannotUnloadPluginException;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackCommand;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class PlaybackRunnerExtended extends PlaybackRunner {
  public static final String NOTIFICATION_GROUP = "PerformancePlugin";

  private Project myProject;
  private boolean myStopped;
  private final DynamicPluginListener myDynamicPluginListener;

  public PlaybackRunnerExtended(String script, StatusCallback callback, @NotNull Project project) {
    super(script, callback, Registry.is("performance.plugin.playback.runner.useDirectActionCall", false), false, true);
    setProject(project);
    myDynamicPluginListener = new DynamicPluginListener() {
      @Override
      public void checkUnloadPlugin(@NotNull IdeaPluginDescriptor pluginDescriptor) throws CannotUnloadPluginException {
        if (PluginId.getId("com.jetbrains.performancePlugin").equals(pluginDescriptor.getPluginId())) {
          throw new CannotUnloadPluginException("Cannot unload plugin during playback execution");
        }
      }
    };
  }

  @Override
  public void setProject(@Nullable Project project) {
    myProject = project;

    if (project != null && !project.isDefault()) {
      Disposer.register(project, () -> {
        if (project == myProject) {
          Disposer.dispose(myOnStop);
        }
      });
    }
  }

  @Override
  @Nullable
  public Project getProject() {
    return myProject;
  }

  @Override
  protected void subscribeListeners(MessageBusConnection connection) {
    super.subscribeListeners(connection);
    connection.subscribe(DynamicPluginListener.TOPIC, myDynamicPluginListener);
  }

  @Override
  protected void onStop() {
    super.onStop();
    myStopped = true;
  }

  @Nullable
  @Override
  protected PlaybackCommand createCommand(@NotNull String _command, int line, @NotNull File scriptDir) {
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
  public ActionCallback run() {
    if (myStopped) {
      throw new IllegalStateException("PlaybackRunnerExtended can be run only once.");
    }
    ActionCallback callback = super.run();
    RunCallbackHandler.applyPatchesToCommandCallback(myProject, callback);
    return callback;
  }
}
