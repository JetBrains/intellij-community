// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MavenDistributionsCache {
  private final ConcurrentMap<String, String> myWorkingDirToMultimoduleMap = ContainerUtil.createConcurrentWeakMap();
  private final ConcurrentMap<String, String> myVmSettingsMap = ContainerUtil.createConcurrentWeakMap();
  private final ConcurrentMap<String, MavenDistribution> myMultimoduleDirToWrapperedMavenDistributionsMap = new ConcurrentHashMap<>();
  private final Project myProject;
  private final ClearableLazyValue<MavenDistribution> mySettingsDistribution = ClearableLazyValue.create(() -> getSettingsDistribution());

  public MavenDistributionsCache(Project project) {
    myProject = project;
  }

  public static MavenDistributionsCache getInstance(Project project) {
    return ServiceManager.getService(project, MavenDistributionsCache.class);
  }

  public void cleanCaches() {
    mySettingsDistribution.drop();
    myWorkingDirToMultimoduleMap.clear();
    myMultimoduleDirToWrapperedMavenDistributionsMap.clear();
    myVmSettingsMap.clear();
  }

  public @NotNull MavenDistribution getSettingsDistribution() {
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
    MavenDistribution distribution = new MavenDistributionConverter().fromString(settings.getGeneralSettings().getMavenHome());
    if (distribution == null) {
      MavenProjectsManager.getInstance(myProject).getSyncConsole().addWarning(SyncBundle.message("cannot.resolve.maven.home"), SyncBundle
        .message("is.not.correct.maven.home.reverting.to.embedded", settings.generalSettings.getMavenHome()));
      return resolveEmbeddedMavenHome();
    }
    else {
      return distribution;
    }
  }

  public @NotNull String getVmOptions(@Nullable String workingDirectory) {
    if (!useWrapper() || workingDirectory == null) {
      return MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().importingSettings.getVmOptionsForImporter();
    }

    String multiModuleDir = myWorkingDirToMultimoduleMap.computeIfAbsent(workingDirectory, this::resolveMultimoduleDirectory);
    return myVmSettingsMap.computeIfAbsent(multiModuleDir, this::readVmOptions);
  }

  private @NotNull String readVmOptions(@NotNull String multiModuleDir) {
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(multiModuleDir);
    if (baseDir == null) return settings.importingSettings.getVmOptionsForImporter();
    VirtualFile mvn = baseDir.findChild(".mvn");
    if (mvn == null) return settings.importingSettings.getVmOptionsForImporter();
    VirtualFile jdkOpts = mvn.findChild("jvm.config");
    if (jdkOpts == null) return settings.importingSettings.getVmOptionsForImporter();
    try {
      return new String(jdkOpts.contentsToByteArray(true), jdkOpts.getCharset());
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
      return settings.importingSettings.getVmOptionsForImporter();
    }
  }

  public @NotNull MavenDistribution getMavenDistribution(@Nullable String workingDirectory) {
    if (!useWrapper() || workingDirectory == null) {
      return mySettingsDistribution.getValue();
    }

    String multiModuleDir = myWorkingDirToMultimoduleMap.computeIfAbsent(workingDirectory, this::resolveMultimoduleDirectory);
    return myMultimoduleDirToWrapperedMavenDistributionsMap.computeIfAbsent(multiModuleDir, this::getWrapperDistribution);
  }

  private @NotNull MavenDistribution getWrapperDistribution(@NotNull String multiModuleDir) {
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
    String distributionUrl = getWrapperDistributionUrl(multiModuleDir);
    if (distributionUrl == null) {
      MavenProjectsManager.getInstance(myProject).getSyncConsole().addWarning(SyncBundle.message("cannot.resolve.maven.home"), SyncBundle
        .message("is.not.correct.maven.home.reverting.to.embedded", settings.generalSettings.getMavenHome()));
      return resolveEmbeddedMavenHome();
    }
    else {
      return doResolveMavenWrapper(distributionUrl, multiModuleDir);
    }
  }

  private MavenDistribution doResolveMavenWrapper(String distributionUrl, String multiModuleDir) {
    MavenSyncConsole console = MavenProjectsManager.getInstance(myProject).getSyncConsole();
    try {
      console.startWrapperResolving();
      MavenDistribution distribution =
        new MavenWrapperSupport().downloadAndInstallMaven(distributionUrl, console.progressIndicatorForWrapper());
      if (distributionUrl.toLowerCase(Locale.ENGLISH).startsWith("http:")) {
        MavenWrapperSupport.showUnsecureWarning(console, LocalFileSystem.getInstance().findFileByPath(multiModuleDir));
      }
      console.finishWrapperResolving(null);
      return distribution;
    }
    catch (Exception e) {
      console.finishWrapperResolving(e);
      LocalMavenDistribution distribution = resolveEmbeddedMavenHome();
      return new LocalMavenDistribution(distribution.getMavenHome(), distributionUrl);
    }
  }

  @NotNull
  public static LocalMavenDistribution resolveEmbeddedMavenHome() {
    final File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenServerManager.class));
    final String root = pluginFileOrDir.getParent();
    LocalMavenDistribution result;
    if (pluginFileOrDir.isDirectory()) {
      File parentFile = MavenUtil.getMavenPluginParentFile();
      File mavenFile = new File(parentFile, "maven36-server-impl/lib/maven3");
      if (mavenFile.isDirectory()) {
        return new LocalMavenDistribution(mavenFile, MavenServerManager.BUNDLED_MAVEN_3);
      }
    }
    else {
      return new LocalMavenDistribution(new File(root, "maven3"), MavenServerManager.BUNDLED_MAVEN_3);
    }

    throw new RuntimeException("run setupBundledMaven.gradle task. Cannot resolve embedded maven home without it");
  }

  private static @Nullable String getWrapperDistributionUrl(String multimoduleDirectory) {
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(multimoduleDirectory);
    if (baseDir == null) {
      return null;
    }
    return MavenWrapperSupport.getWrapperDistributionUrl(baseDir);
  }

  private @NotNull String resolveMultimoduleDirectory(@NotNull String workingDirectory) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
    if (!manager.isMavenizedProject()) {
      return FileUtil.toSystemIndependentName(calculateMultimoduleDirUpToFileTree(workingDirectory));
    }
    return FileUtil.toSystemIndependentName(manager.getRootProjects().stream()
                                              .map(p -> p.getDirectory())
                                              .filter(rpDirectory -> FileUtil.isAncestor(rpDirectory, workingDirectory, false))
                                              .findFirst()
                                              .orElse(workingDirectory));
  }

  private @NotNull String calculateMultimoduleDirUpToFileTree(String directory) {
    VirtualFile path = LocalFileSystem.getInstance().findFileByPath(directory);
    if(path == null) return directory;
    Collection<String> knownWorkingDirs = myWorkingDirToMultimoduleMap.values();
    for (String known: knownWorkingDirs) {
      if(FileUtil.isAncestor(known, directory, false)) {
        return known;
      }
    }
    return MavenUtil.getVFileBaseDir(path).getPath();
  }

  public @NotNull String getMultimoduleDirectory(@NotNull String workingDirectory) {
    return myWorkingDirToMultimoduleMap.computeIfAbsent(workingDirectory, this::resolveMultimoduleDirectory);
  }

  private boolean useWrapper() {
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
    return MavenServerManager.WRAPPED_MAVEN.equals(settings.generalSettings.getMavenHome()) ||
           StringUtil.equals(settings.generalSettings.getMavenHome(), MavenProjectBundle.message("maven.wrapper.version.title"));
  }
}
