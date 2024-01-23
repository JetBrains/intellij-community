// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenExternalParameters;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.impl.BundledMavenDownloader;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.jetbrains.idea.maven.utils.MavenUtil.isValidMavenHome;

@Service(Service.Level.PROJECT)
public final class MavenDistributionsCache {
  private final static ClearableLazyValue<Path> mySourcePath = ClearableLazyValue.create(MavenDistributionsCache::getSourceMavenPath);

  private final ConcurrentMap<String, String> myWorkingDirToMultiModuleMap = CollectionFactory.createConcurrentWeakMap();
  private final ConcurrentMap<String, String> myVmSettingsMap = CollectionFactory.createConcurrentWeakMap();
  private final ConcurrentMap<String, MavenDistribution> myMultimoduleDirToWrapperedMavenDistributionsMap = new ConcurrentHashMap<>();
  private final Project myProject;
  private final ClearableLazyValue<MavenDistribution> mySettingsDistribution = ClearableLazyValue.create(this::getSettingsDistribution);

  public MavenDistributionsCache(Project project) {
    myProject = project;
  }

  public static MavenDistributionsCache getInstance(Project project) {
    return project.getService(MavenDistributionsCache.class);
  }

  public void cleanCaches() {
    mySettingsDistribution.drop();
    myWorkingDirToMultiModuleMap.clear();
    myMultimoduleDirToWrapperedMavenDistributionsMap.clear();
    myVmSettingsMap.clear();
  }

  public @NotNull MavenDistribution getSettingsDistribution() {
    var projectsManager = MavenProjectsManager.getInstance(myProject);
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
    MavenHomeType type = settings.getGeneralSettings().getMavenHomeType();
    if (type instanceof MavenWrapper) {
      var baseDir = myProject.getBasePath();
      var projects = projectsManager.getProjects();
      if (projects.size() > 0) {
        baseDir = projects.get(0).getDirectory();
      }
      if (baseDir != null) {
        String multiModuleDir = myWorkingDirToMultiModuleMap.computeIfAbsent(baseDir, this::resolveMultiModuleDirectory);
        return myMultimoduleDirToWrapperedMavenDistributionsMap.computeIfAbsent(multiModuleDir, this::getWrapperDistribution);
      }
    }
    else if (type instanceof MavenInSpecificPath sp) {
      MavenDistribution mavenDistribution = fromPath(sp.getMavenHome(), sp.getTitle());
      if (mavenDistribution != null) return mavenDistribution;
    }
    else if (type instanceof BundledMaven3 || type instanceof BundledMaven4) {
      return resolveEmbeddedMavenHome();
    }


    projectsManager.getSyncConsole().addWarning(SyncBundle.message("cannot.resolve.maven.home"), SyncBundle
      .message("is.not.correct.maven.home.reverting.to.embedded", settings.getGeneralSettings().getMavenHomeType().getTitle()));
      return resolveEmbeddedMavenHome();

  }

  @Nullable
  private static MavenDistribution fromPath(@NotNull String path, @NotNull String label) {
    File file = new File(path);
    if (!isValidMavenHome(file)) return null;
    WslPath wslPath = WslPath.parseWindowsUncPath(file.getAbsolutePath());
    if (wslPath == null) {
      return new LocalMavenDistribution(file.toPath(), label);
    }
    else {
      return new WslMavenDistribution(wslPath.getDistribution(), wslPath.getLinuxPath(), label);
    }
  }

  public @NotNull String getVmOptions(@Nullable String workingDirectory) {
    String vmOptions = MavenWorkspaceSettingsComponent.getInstance(myProject)
      .getSettings().getImportingSettings().getVmOptionsForImporter();
    if (workingDirectory == null || !StringUtil.isEmptyOrSpaces(vmOptions)) {
      return vmOptions;
    }

    String multiModuleDir = myWorkingDirToMultiModuleMap.computeIfAbsent(workingDirectory, this::resolveMultiModuleDirectory);
    return myVmSettingsMap.computeIfAbsent(multiModuleDir, MavenExternalParameters::readJvmConfigOptions);
  }

  public @NotNull MavenDistribution getMavenDistribution(@Nullable String workingDirectory) {
    if (!useWrapper() || workingDirectory == null) {
      return mySettingsDistribution.getValue();
    }

    String multiModuleDir = myWorkingDirToMultiModuleMap.computeIfAbsent(workingDirectory, this::resolveMultiModuleDirectory);
    return myMultimoduleDirToWrapperedMavenDistributionsMap.computeIfAbsent(multiModuleDir, this::getWrapperDistribution);
  }

  void addWrapper(@NotNull String workingDirectory, @NotNull MavenDistribution distribution) {
    myMultimoduleDirToWrapperedMavenDistributionsMap.put(workingDirectory, distribution);
  }

  private @NotNull MavenDistribution getWrapperDistribution(@NotNull String multiModuleDir) {
    String distributionUrl = getWrapperDistributionUrl(multiModuleDir);
    return (distributionUrl == null) ? resolveEmbeddedMavenHome() : getMavenWrapper(distributionUrl);
  }

  public @Nullable MavenDistribution getWrapper(@NotNull String workingDirectory) {
    String multiModuleDir = myWorkingDirToMultiModuleMap.computeIfAbsent(workingDirectory, this::resolveMultiModuleDirectory);
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
    if (PluginManagerCore.isRunningFromSources()) {
      Path mavenPath = mySourcePath.getValue();
      return new LocalMavenDistribution(mavenPath, BundledMaven3.INSTANCE.getTitle());
    }
    else {
      final Path pluginFileOrDir = Path.of(PathUtil.getJarPathForClass(MavenServerManager.class));
      final Path root = pluginFileOrDir.getParent();

      // maven3 folder inside maven plugin layout
      return new LocalMavenDistribution(root.resolve("maven3"), BundledMaven3.INSTANCE.getTitle());
    }
  }

  private static Path getSourceMavenPath() {
    BuildDependenciesCommunityRoot communityRoot = new BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath()));
    return BundledMavenDownloader.INSTANCE.downloadMavenDistributionSync(communityRoot);
  }

  @Nullable
  String getWrapperDistributionUrl(String multimoduleDirectory) {
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(multimoduleDirectory);
    if (baseDir == null) {
      return null;
    }
    return MavenWrapperSupport.getWrapperDistributionUrl(baseDir);
  }

  private @NotNull String resolveMultiModuleDirectory(@NotNull String workingDirectory) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
    if (!manager.isMavenizedProject()) {
      return FileUtilRt.toSystemIndependentName(calculateMultimoduleDirUpToFileTree(workingDirectory));
    }
    return FileUtilRt.toSystemIndependentName(manager.getRootProjects().stream()
                                              .map(MavenProject::getDirectory)
                                              .filter(rpDirectory -> FileUtil.isAncestor(rpDirectory, workingDirectory, false))
                                              .findFirst()
                                              .orElseGet(() -> calculateMultimoduleDirUpToFileTree(workingDirectory)));
  }

  private @NotNull String calculateMultimoduleDirUpToFileTree(String directory) {
    VirtualFile path = LocalFileSystem.getInstance().findFileByPath(directory);
    if (path == null) return directory;
    Collection<String> knownWorkingDirs = myWorkingDirToMultiModuleMap.values();
    for (String known : knownWorkingDirs) {
      if (FileUtil.isAncestor(known, directory, false)) {
        return known;
      }
    }
    return MavenUtil.getVFileBaseDir(path).getPath();
  }

  public @NotNull String getMultimoduleDirectory(@NotNull String workingDirectory) {
    return myWorkingDirToMultiModuleMap.computeIfAbsent(workingDirectory, this::resolveMultiModuleDirectory);
  }

  private boolean useWrapper() {
    MavenWorkspaceSettings settings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
    return MavenUtil.isWrapper(settings.getGeneralSettings());
  }
}
