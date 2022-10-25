package com.jetbrains.performancePlugin.profilers;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public interface Profiler {
  String PROFILER_PROPERTY = "integrationTests.profiler";
  ExtensionPointName<Profiler> EP_NAME = new ExtensionPointName<>("com.jetbrains.performancePlugin.profiler");

  static boolean isAnyProfilingStarted() {
    return ContainerUtil.exists(EP_NAME.getExtensionList(), it -> it.isProfilingStarted());
  }

  static Profiler getCurrentProfilerHandler() {
    List<Profiler> all = ContainerUtil.findAll(EP_NAME.getExtensionList(), it -> it.isEnabled());
    assert all.size() == 1;
    return all.get(0);
  }

  static Profiler getCurrentProfilerHandler(Project project) {
    List<Profiler> all = ContainerUtil.findAll(EP_NAME.getExtensionList(), it -> it.isEnabledInProject(project));
    assert all.size() == 1;
    return all.get(0);
  }

  @NotNull
  static String formatSnapshotName(boolean isMemorySnapshot) {
    String buildNumber = ApplicationInfo.getInstance().getBuild().asString();
    String userName = SystemProperties.getUserName();
    String snapshotDate = new SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(new Date());
    return buildNumber + '_' + (isMemorySnapshot ? "memory_" : "") + userName + '_' + snapshotDate;
  }

  void startProfiling(@NotNull String activityName, @NotNull List<String> options);

  @NotNull
  String stopProfiling(@NotNull List<String> options) throws Exception;

  @NotNull
  String stopProfileWithNotification(ActionCallback actionCallback, String arguments);

  File compressResults(@NotNull String pathToResult, @NotNull String archiveName) throws IOException;

  boolean isEnabled();

  boolean isEnabledInProject(Project project);

  boolean isProfilingStarted();
}
