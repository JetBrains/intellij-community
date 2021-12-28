// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

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
import org.jetbrains.idea.maven.execution.MavenExternalParameters;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
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
    return project.getService(MavenDistributionsCache.class);
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
    String vmOptions = MavenWorkspaceSettingsComponent.getInstance(myProject)
      .getSettings().getImportingSettings().getVmOptionsForImporter();
    if (workingDirectory == null || !StringUtil.isEmptyOrSpaces(vmOptions)) {
      return vmOptions;
    }

    String multiModuleDir = myWorkingDirToMultimoduleMap.computeIfAbsent(workingDirectory, this::resolveMultimoduleDirectory);
    return myVmSettingsMap.computeIfAbsent(multiModuleDir, MavenExternalParameters::readJvmConfigOptions);
  }

  public @NotNull MavenDistribution getMavenDistribution(@Nullable String workingDirectory) {
    if (!useWrapper() || workingDirectory == null) {
      return mySettingsDistribution.getValue();
    }

    String multiModuleDir = myWorkingDirToMultimoduleMap.computeIfAbsent(workingDirectory, this::resolveMultimoduleDirectory);
    return myMultimoduleDirToWrapperedMavenDistributionsMap.computeIfAbsent(multiModuleDir, this::getWrapperDistribution);
  }

  void addWrapper(@NotNull String workingDirectory, @NotNull MavenDistribution distribution) {
    myMultimoduleDirToWrapperedMavenDistributionsMap.put(workingDirectory, distribution);
  }

  private @NotNull MavenDistribution getWrapperDistribution(@NotNull String multiModuleDir) {
    String distributionUrl = getWrapperDistributionUrl(multiModuleDir);
    return  (distributionUrl == null) ? resolveEmbeddedMavenHome() : getMavenWrapper(distributionUrl);
  }

  public @Nullable MavenDistribution getWrapper(@NotNull String workingDirectory) {
    String multiModuleDir = myWorkingDirToMultimoduleMap.computeIfAbsent(workingDirectory, this::resolveMultimoduleDirectory);
    String distributionUrl = getWrapperDistributionUrl(multiModuleDir);
    return (distributionUrl != null) ? MavenWrapperSupport.getCurrentDistribution(distributionUrl) : null;
  }

  private static MavenDistribution getMavenWrapper(String distributionUrl) {
    MavenDistribution distribution = MavenWrapperSupport.getCurrentDistribution(distributionUrl);
    if (distribution == null) {
      distribution = resolveEmbeddedMavenHome();
    }
    return distribution;
  }

  @NotNull
  public static LocalMavenDistribution resolveEmbeddedMavenHome() {
    final Path pluginFileOrDir = Path.of(PathUtil.getJarPathForClass(MavenServerManager.class));
    final Path root = pluginFileOrDir.getParent();
    if (Files.isDirectory(pluginFileOrDir)) {
      Path parentPath = MavenUtil.getMavenPluginParentFile().toPath();
      Path mavenPath = parentPath.resolve("maven36-server-impl/lib/maven3");
      if (Files.isDirectory(mavenPath)) {
        return new LocalMavenDistribution(mavenPath, MavenServerManager.BUNDLED_MAVEN_3);
      }
    }
    else {
      return new LocalMavenDistribution(root.resolve("maven3"), MavenServerManager.BUNDLED_MAVEN_3);
    }

    throw new RuntimeException("run \"Download Bundled Maven\" run configuration. Cannot resolve embedded maven home without it");
  }

  @Nullable
  String getWrapperDistributionUrl(String multimoduleDirectory) {
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(multimoduleDirectory);
    if (baseDir == null) {
      return null;
    }
    String distributionUrl = MavenWrapperSupport.getWrapperDistributionUrl(baseDir);
    if (distributionUrl == null) {
      MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
      MavenProjectsManager.getInstance(myProject).getSyncConsole()
        .addWarning(
          SyncBundle.message("cannot.resolve.maven.home"),
          SyncBundle.message("is.not.correct.maven.home.reverting.to.embedded", settings.getGeneralSettings().getMavenHome())
        );
    }
    return distributionUrl;
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
                                              .orElseGet(() -> calculateMultimoduleDirUpToFileTree(workingDirectory)));
  }

  private @NotNull String calculateMultimoduleDirUpToFileTree(String directory) {
    VirtualFile path = LocalFileSystem.getInstance().findFileByPath(directory);
    if (path == null) return directory;
    Collection<String> knownWorkingDirs = myWorkingDirToMultimoduleMap.values();
    for (String known : knownWorkingDirs) {
      if (FileUtil.isAncestor(known, directory, false)) {
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
    return MavenUtil.isWrapper(settings.getGeneralSettings());
  }
}
