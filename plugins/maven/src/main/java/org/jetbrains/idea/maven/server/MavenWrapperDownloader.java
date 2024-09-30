// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.build.events.impl.ProgressBuildEventImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.project.BundledMaven3;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.Locale;

public final class MavenWrapperDownloader {

  public static void checkOrInstall(@NotNull Project project, @Nullable String workingDir) {
    checkOrInstall(project, workingDir, null, true);
  }

  public static void checkOrInstallForSync(@NotNull Project project,
                                           @Nullable String workingDir,
                                           boolean showNotificationIfUrlMissing) {
    checkOrInstall(project, workingDir, MavenProjectsManager.getInstance(project).getSyncConsole(), showNotificationIfUrlMissing);
  }

  private static synchronized void checkOrInstall(@NotNull Project project,
                                                  @Nullable String workingDir,
                                                  @Nullable MavenSyncConsole syncConsole,
                                                  boolean showNotificationIfUrlMissing) {
    if (workingDir == null) return;
    MavenDistributionsCache distributionsCache = MavenDistributionsCache.getInstance(project);

    String multiModuleDir = distributionsCache.getMultimoduleDirectory(workingDir);
    String distributionUrl = distributionsCache.getWrapperDistributionUrl(multiModuleDir);
    if (distributionUrl == null) {
      if (showNotificationIfUrlMissing) {
        MavenWrapperEventLogNotification.noDistributionUrlEvent(project, multiModuleDir);
      }
      return;
    }

    MavenDistribution distribution = MavenWrapperSupport.getCurrentDistribution(distributionUrl);
    if (distribution != null) return;

    MavenLog.LOG.info("start install wrapper " + distributionUrl);

    if (syncConsole != null) syncConsole.startWrapperResolving();

    Task.Backgroundable task = getTaskInfo();
    BackgroundableProcessIndicator indicator = new WrapperProgressIndicator(project, task, syncConsole);
    try {
      distribution = new MavenWrapperSupport().downloadAndInstallMaven(distributionUrl, indicator, project);
      if (syncConsole != null && distributionUrl.toLowerCase(Locale.ENGLISH).startsWith("http:")) {
        MavenWrapperSupport.showUnsecureWarning(syncConsole, LocalFileSystem.getInstance().findFileByPath(multiModuleDir));
      }
      distributionsCache.addWrapper(multiModuleDir, distribution);
      if (syncConsole != null) syncConsole.finishWrapperResolving(null);
    }
    catch (Exception e) {
      MavenLog.LOG.warn("error install wrapper", e);
      if (syncConsole != null) syncConsole.finishWrapperResolving(e);
      MavenWrapperEventLogNotification.errorDownloading(project, e.getLocalizedMessage());
      if (syncConsole != null) {
        MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
        settings.getGeneralSettings().setMavenHomeType(BundledMaven3.INSTANCE);
      }
    }
    finally {
      indicator.finish(task);
      MavenLog.LOG.info("finish install wrapper " + distributionUrl);
    }
  }

  @NotNull
  private static Task.Backgroundable getTaskInfo() {
    return new Task.Backgroundable(null, SyncBundle.message("maven.sync.wrapper.downloading")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) { }
    };
  }

  private static class WrapperProgressIndicator extends BackgroundableProcessIndicator {
    private long myFraction = 0;
    private final MavenSyncConsole mySyncConsole;

    private WrapperProgressIndicator(@NotNull Project project, @NotNull Task.Backgroundable task, @Nullable MavenSyncConsole syncConsole) {
      super(project, task);
      mySyncConsole = syncConsole;
    }

    @Override
    public void setText(String text) {
      super.setText(text);
      if (mySyncConsole != null && text != null) {
        mySyncConsole.addWrapperProgressText(text);
      }
    }

    @Override
    public void setFraction(double fraction) {
      super.setFraction(fraction);
      if (mySyncConsole != null) {
        long newFraction = Math.round(fraction * 100);
        if (myFraction == newFraction) return;
        myFraction = newFraction;

        ProgressBuildEventImpl event = new ProgressBuildEventImpl(
          SyncBundle.message("maven.sync.wrapper"), SyncBundle.message("maven.sync.wrapper"),
          System.currentTimeMillis(),
          SyncBundle.message("maven.sync.wrapper.downloading.progress", myFraction, 100),
          100,
          myFraction,
          "%");
        mySyncConsole.addBuildEvent(event);
      }
    }
  }
}