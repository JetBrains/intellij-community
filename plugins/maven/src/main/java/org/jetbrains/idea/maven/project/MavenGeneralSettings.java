// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.config.MavenConfig;
import org.jetbrains.idea.maven.config.MavenConfigParser;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.MavenWslUtil;

import java.io.File;
import java.util.*;

import static java.util.Objects.requireNonNullElse;
import static org.jetbrains.idea.maven.config.MavenConfigSettings.*;

public class MavenGeneralSettings implements Cloneable {
  private transient Project myProject;
  private boolean workOffline = false;
  private String mavenHome = MavenServerManager.BUNDLED_MAVEN_3;
  private String mavenSettingsFile = "";
  private String overriddenLocalRepository = "";
  private boolean printErrorStackTraces = false;
  private boolean usePluginRegistry = false;
  private boolean nonRecursive = false;
  private boolean alwaysUpdateSnapshots = false;
  private boolean tychoProject = false;
  private boolean showDialogWithAdvancedSettings = false;
  private boolean useMavenConfig = false;
  private String threads;

  private MavenExecutionOptions.LoggingLevel outputLevel = MavenExecutionOptions.LoggingLevel.INFO;
  MavenExecutionOptions.ChecksumPolicy checksumPolicy = MavenExecutionOptions.ChecksumPolicy.NOT_SET;
  private MavenExecutionOptions.FailureMode failureBehavior = MavenExecutionOptions.FailureMode.NOT_SET;
  private MavenExecutionOptions.PluginUpdatePolicy pluginUpdatePolicy = MavenExecutionOptions.PluginUpdatePolicy.DEFAULT;

  private transient File myEffectiveLocalRepositoryCache;
  private transient File myEffectiveLocalHomeCache;
  private transient VirtualFile myEffectiveSuperPomCache;
  private transient Set<String> myDefaultPluginsCache;
  private transient MavenConfig mavenConfigCache;

  private int myBulkUpdateLevel = 0;
  private List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public MavenGeneralSettings() {
  }

  public MavenGeneralSettings(Project project) {
    myProject = project;
  }

  public void setProject(Project project) {
    myProject = project;
  }

  public void beginUpdate() {
    myBulkUpdateLevel++;
  }

  public void endUpdate() {
    if (--myBulkUpdateLevel == 0) {
      changed();
    }
  }

  public void changed() {
    if (myBulkUpdateLevel > 0) return;

    myEffectiveLocalRepositoryCache = null;
    myDefaultPluginsCache = null;
    myEffectiveLocalHomeCache = null;
    myEffectiveSuperPomCache = null;
    mavenConfigCache = null;
    fireChanged();
  }

  @Property
  @NotNull
  public MavenExecutionOptions.PluginUpdatePolicy getPluginUpdatePolicy() {
    return pluginUpdatePolicy;
  }

  public void setPluginUpdatePolicy(MavenExecutionOptions.PluginUpdatePolicy value) {
    if (value == null) return; // null may come from deserializer
    this.pluginUpdatePolicy = value;
    changed();
  }

  @Property
  @NotNull
  public MavenExecutionOptions.ChecksumPolicy getChecksumPolicy() {
    return checksumPolicy;
  }

  public void setChecksumPolicy(MavenExecutionOptions.ChecksumPolicy value) {
    if (value == null) return; // null may come from deserializer
    if (!Comparing.equal(this.checksumPolicy, value)) {
      this.checksumPolicy = value;
      changed();
    }
  }

  @Property
  @NotNull
  public MavenExecutionOptions.FailureMode getFailureBehavior() {
    return failureBehavior;
  }

  public void setFailureBehavior(MavenExecutionOptions.FailureMode value) {
    if (value == null) return; // null may come from deserializer
    if (!Comparing.equal(this.failureBehavior, value)) {
      this.failureBehavior = value;
      changed();
    }
  }

  /**
   * @deprecated use {@link #getOutputLevel()}
   */
  @Transient
  @NotNull
  @Deprecated
  public MavenExecutionOptions.LoggingLevel getLoggingLevel() {
    return getOutputLevel();
  }

  @Property
  @NotNull
  public MavenExecutionOptions.LoggingLevel getOutputLevel() {
    return outputLevel;
  }

  public void setOutputLevel(MavenExecutionOptions.LoggingLevel value) {
    if (value == null) return; // null may come from deserializer
    if (!Comparing.equal(this.outputLevel, value)) {
      this.outputLevel = value;
      changed();
    }
  }

  public boolean isWorkOffline() {
    return workOffline;
  }

  public void setWorkOffline(boolean value) {
    if (!Comparing.equal(this.workOffline, value)) {
      this.workOffline = value;
      changed();
    }
  }

  @NotNull
  public String getMavenHome() {
    return mavenHome;
  }

  public void setMavenHome(@NotNull final String mavenHome) {
    final File mavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(mavenHome);
    final File bundledMavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(MavenServerManager.BUNDLED_MAVEN_3);

    String mavenHomeToSet = mavenHome;
    if (FileUtil.filesEqual(mavenHomeDirectory, bundledMavenHomeDirectory)) {
      mavenHomeToSet = MavenServerManager.BUNDLED_MAVEN_3;
    }
    if (!Objects.equals(this.mavenHome, mavenHomeToSet)) {
      this.mavenHome = mavenHomeToSet;
      myDefaultPluginsCache = null;
      changed();
    }
  }

  /** @deprecated use {@link MavenUtil} or {@link MavenWslUtil} instead */
  @Deprecated(forRemoval = true)
  public @Nullable File getEffectiveMavenHome() {
    if (myEffectiveLocalHomeCache == null) {
      myEffectiveLocalHomeCache = MavenWslUtil.resolveMavenHome(myProject, getMavenHome());
    }
    return myEffectiveLocalHomeCache;
  }

  @NotNull
  public String getUserSettingsFile() {
    return mavenSettingsFile;
  }

  public void setUserSettingsFile(@Nullable String mavenSettingsFile) {
    if (mavenSettingsFile == null) return;

    if (!Objects.equals(this.mavenSettingsFile, mavenSettingsFile)) {
      this.mavenSettingsFile = mavenSettingsFile;
      changed();
    }
  }

  /** @deprecated use {@link MavenUtil} or {@link MavenWslUtil} instead */
  @Deprecated(forRemoval = true)
  public @Nullable File getEffectiveUserSettingsIoFile() {
    return MavenWslUtil.getUserSettings(myProject, getUserSettingsFile(), getMavenConfig());
  }
  /** @deprecated use {@link MavenUtil} or {@link MavenWslUtil} instead */
  @Deprecated
  public @Nullable File getEffectiveGlobalSettingsIoFile() {
    return MavenWslUtil.getGlobalSettings(myProject, getMavenHome(), getMavenConfig());
  }

  /** @deprecated use {@link MavenUtil} or {@link MavenWslUtil} instead */
  @Deprecated
  public @Nullable VirtualFile getEffectiveUserSettingsFile() {
    File file = getEffectiveUserSettingsIoFile();
    return file == null ? null : LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  /** @deprecated use {@link MavenUtil} or {@link MavenWslUtil} instead */
  @Deprecated
  public List<VirtualFile> getEffectiveSettingsFiles() {
    List<VirtualFile> result = new ArrayList<>(2);
    VirtualFile file = getEffectiveUserSettingsFile();
    if (file != null) result.add(file);
    file = getEffectiveGlobalSettingsFile();
    if (file != null) result.add(file);
    return result;
  }

  /** @deprecated use {@link MavenUtil} or {@link MavenWslUtil} instead */
  @Deprecated
  public @Nullable VirtualFile getEffectiveGlobalSettingsFile() {
    File file = getEffectiveGlobalSettingsIoFile();
    return file == null ? null : LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  @NotNull
  public String getLocalRepository() {
    return overriddenLocalRepository;
  }

  public void setLocalRepository(final @Nullable String overriddenLocalRepository) {
    if (overriddenLocalRepository == null) return;

    if (!Objects.equals(this.overriddenLocalRepository, overriddenLocalRepository)) {
      this.overriddenLocalRepository = overriddenLocalRepository;
      if (myProject != null) {
        MavenUtil.restartMavenConnectors(myProject, false);
      }
      changed();
    }
  }

  /** @deprecated use {@link MavenUtil} or {@link MavenWslUtil} instead */
  @Deprecated(forRemoval = true)
  public File getEffectiveLocalRepository() {
    File result = myEffectiveLocalRepositoryCache;
    if (result != null) return result;

    result = MavenWslUtil.getLocalRepo(myProject, overriddenLocalRepository, mavenHome, mavenSettingsFile, getMavenConfig());
    myEffectiveLocalRepositoryCache = result;
    return result;
  }

  /** @deprecated use {@link MavenUtil} or {@link MavenWslUtil} instead */
  @Deprecated
  public @Nullable VirtualFile getEffectiveSuperPom() {
    VirtualFile result = myEffectiveSuperPomCache;
    if (result != null && result.isValid()) {
      return result;
    }
    result = MavenUtil.resolveSuperPomFile(getEffectiveMavenHome());
    myEffectiveSuperPomCache = result;
    return result;
  }

  @SuppressWarnings("unused")
  public boolean isDefaultPlugin(String groupId, String artifactId) {
    return getDefaultPlugins().contains(groupId + ":" + artifactId);
  }

  private Set<String> getDefaultPlugins() {
    Set<String> result = myDefaultPluginsCache;
    if (result != null) return result;

    result = new HashSet<>();

    VirtualFile effectiveSuperPom = getEffectiveSuperPom();
    if (effectiveSuperPom != null) {
      Element superProject = MavenJDOMUtil.read(effectiveSuperPom, null);
      for (Element each : MavenJDOMUtil.findChildrenByPath(superProject, "build.pluginManagement.plugins", "plugin")) {
        String groupId = MavenJDOMUtil.findChildValueByPath(each, "groupId", "org.apache.maven.plugins");
        String artifactId = MavenJDOMUtil.findChildValueByPath(each, "artifactId", null);
        result.add(groupId + ":" + artifactId);
      }
    }

    myDefaultPluginsCache = result;
    return result;
  }

  public boolean isPrintErrorStackTraces() {
    return printErrorStackTraces;
  }

  public void setPrintErrorStackTraces(boolean value) {
    if (!Comparing.equal(this.printErrorStackTraces, value)) {
      printErrorStackTraces = value;
      changed();
    }
  }

  public boolean isUsePluginRegistry() {
    return usePluginRegistry;
  }

  public void setUsePluginRegistry(final boolean value) {
    if (!Comparing.equal(this.usePluginRegistry, value)) {
      this.usePluginRegistry = value;
      changed();
    }
  }

  public boolean isUseMavenConfig() {
    return useMavenConfig;
  }

  public void setUseMavenConfig(boolean value) {
    if (!Comparing.equal(this.useMavenConfig, value)) {
      this.useMavenConfig = value;
      changed();
    }
  }

  public boolean isAlwaysUpdateSnapshots() {
    return alwaysUpdateSnapshots;
  }

  public boolean isTychoProject() {
    return tychoProject;
  }

  public void setAlwaysUpdateSnapshots(boolean value) {
    if (!Comparing.equal(this.alwaysUpdateSnapshots, value)) {
      this.alwaysUpdateSnapshots = value;
      changed();
    }
  }

  public void setTychoProject(final boolean tychoProject) {
    if (this.tychoProject != tychoProject) {
      this.tychoProject = tychoProject;
      changed();
    }
  }

  public boolean isShowDialogWithAdvancedSettings() {
    return showDialogWithAdvancedSettings;
  }

  public void setShowDialogWithAdvancedSettings(boolean value) {
    if (!Comparing.equal(this.showDialogWithAdvancedSettings, value)) {
      this.showDialogWithAdvancedSettings = value;
      changed();
    }
  }

  public boolean isNonRecursive() {
    return nonRecursive;
  }

  public void setNonRecursive(final boolean value) {
    if (!Comparing.equal(this.nonRecursive, value)) {
      this.nonRecursive = value;
      changed();
    }
  }

  @Nullable
  public String getThreads() {
    return threads;
  }

  public void setThreads(@Nullable String value) {
    String nullizeValue = StringUtil.nullize(value);
    if (!Objects.equals(this.threads, nullizeValue)) {
      this.threads = nullizeValue;
      changed();
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenGeneralSettings that = (MavenGeneralSettings)o;

    if (nonRecursive != that.nonRecursive) return false;
    if (outputLevel != that.outputLevel) return false;
    if (pluginUpdatePolicy != that.pluginUpdatePolicy) return false;
    if (alwaysUpdateSnapshots != that.alwaysUpdateSnapshots) return false;
    if (tychoProject != that.tychoProject) return false;
    if (showDialogWithAdvancedSettings != that.showDialogWithAdvancedSettings) return false;
    if (printErrorStackTraces != that.printErrorStackTraces) return false;
    if (usePluginRegistry != that.usePluginRegistry) return false;
    if (useMavenConfig != that.useMavenConfig) return false;
    if (workOffline != that.workOffline) return false;
    if (!checksumPolicy.equals(that.checksumPolicy)) return false;
    if (!failureBehavior.equals(that.failureBehavior)) return false;
    if (!overriddenLocalRepository.equals(that.overriddenLocalRepository)) return false;
    if (!mavenHome.equals(that.mavenHome)) return false;
    if (!mavenSettingsFile.equals(that.mavenSettingsFile)) return false;
    if (!Objects.equals(threads, that.threads)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (workOffline ? 1 : 0);
    result = 31 * result + mavenHome.hashCode();
    result = 31 * result + mavenSettingsFile.hashCode();
    result = 31 * result + overriddenLocalRepository.hashCode();
    result = 31 * result + (printErrorStackTraces ? 1 : 0);
    result = 31 * result + (usePluginRegistry ? 1 : 0);
    result = 31 * result + (useMavenConfig ? 1 : 0);
    result = 31 * result + (nonRecursive ? 1 : 0);
    result = 31 * result + outputLevel.hashCode();
    result = 31 * result + checksumPolicy.hashCode();
    result = 31 * result + failureBehavior.hashCode();
    result = 31 * result + pluginUpdatePolicy.hashCode();
    return result;
  }

  @Override
  public MavenGeneralSettings clone() {
    try {
      MavenGeneralSettings result = (MavenGeneralSettings)super.clone();
      result.myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
      result.myBulkUpdateLevel = 0;
      result.setProject(myProject);
      return result;
    }
    catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }

  public void addListener(Listener l, Disposable parentDisposable) {
    addListener(l);
    Disposer.register(parentDisposable, () -> removeListener(l));
  }

  public void addListener(Listener l) {
    myListeners.add(l);
  }

  public void removeListener(Listener l) {
    myListeners.remove(l);
  }

  public void copyListeners(MavenGeneralSettings another) {
    myListeners.addAll(another.myListeners);
  }

  @Transient
  public void updateFromMavenConfig(@NotNull List<VirtualFile> mavenRootProjects) {
    if (mavenRootProjects.isEmpty() || !useMavenConfig) return;
    mavenConfigCache = null;

    VirtualFile file = mavenRootProjects.get(0);
    MavenConfig config = MavenConfigParser.parse(file.isDirectory() ? file.getPath() : file.getParent().getPath());
    mavenConfigCache = config;
    if (config == null) return;

    boolean needUpdate;
    MavenExecutionOptions.ChecksumPolicy checksumConfig = requireNonNullElse(config.getChecksumPolicy(),
                                                                             MavenExecutionOptions.ChecksumPolicy.NOT_SET);
    needUpdate = !Objects.equals(checksumConfig, checksumPolicy);
    checksumPolicy = checksumConfig;

    MavenExecutionOptions.FailureMode failureBehaviorConfig = requireNonNullElse(config.getFailureMode(),
                                                                          MavenExecutionOptions.FailureMode.NOT_SET);
    needUpdate = needUpdate || !Objects.equals(failureBehavior, failureBehaviorConfig);
    failureBehavior = failureBehaviorConfig;

    MavenExecutionOptions.LoggingLevel outputLevelConfig = requireNonNullElse(config.getOutputLevel(),
                                                                              MavenExecutionOptions.LoggingLevel.INFO);
    needUpdate = needUpdate || !Objects.equals(outputLevel, outputLevelConfig);
    outputLevel = outputLevelConfig;

    Boolean offlineConfig = config.hasOption(OFFLINE);
    needUpdate = needUpdate || !Objects.equals(workOffline, offlineConfig);
    workOffline = offlineConfig;

    Boolean stackTracesConfig = config.hasOption(ERRORS);
    needUpdate = needUpdate || !Objects.equals(printErrorStackTraces, stackTracesConfig);
    printErrorStackTraces = stackTracesConfig;

    Boolean updateSnapshotsConfig = config.hasOption(UPDATE_SNAPSHOTS);
    needUpdate = needUpdate || !Objects.equals(alwaysUpdateSnapshots, updateSnapshotsConfig);
    alwaysUpdateSnapshots = updateSnapshotsConfig;

    Boolean nonRecursiveConfig = config.hasOption(NON_RECURSIVE);
    needUpdate = needUpdate || !Objects.equals(nonRecursive, nonRecursiveConfig);
    nonRecursive = nonRecursiveConfig;

    String threadsConfig = StringUtil.nullize(config.getOptionValue(THREADS));
    needUpdate = needUpdate || !Objects.equals(StringUtil.nullize(threads), threadsConfig);
    threads = threadsConfig;

    if (needUpdate) {
      changed();
    }
    mavenConfigCache = config;
  }

  @Transient
  public MavenConfig getMavenConfig() {
    if (!useMavenConfig) return null;
    if (mavenConfigCache != null) return mavenConfigCache;

    MavenProjectsManager instance = myProject != null ? MavenProjectsManager.getInstance(myProject) : null;
    if (instance == null) return null;

    updateFromMavenConfig(MavenUtil.collectFiles(instance.getRootProjects()));
    return mavenConfigCache;
  }

  private void fireChanged() {
    for (Listener each : myListeners) {
      each.changed();
    }
  }

  public interface Listener {
    void changed();
  }
}
