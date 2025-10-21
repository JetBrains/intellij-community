// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.config.MavenConfig;
import org.jetbrains.idea.maven.config.MavenConfigParser;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNullElse;
import static org.jetbrains.idea.maven.config.MavenConfigSettings.*;
import static org.jetbrains.idea.maven.project.MavenHomeKt.resolveMavenHomeType;

public class MavenGeneralSettings implements Cloneable {
  private static final MavenHomeType DEFAULT_MAVEN = BundledMaven3.INSTANCE;
  private transient boolean myForPersistence = false;
  private transient Project myProject;
  private boolean workOffline = false;
  private MavenHomeType mavenHomeType = DEFAULT_MAVEN;
  private String mavenSettingsFile = "";
  private String overriddenLocalRepository = "";
  private boolean printErrorStackTraces = false;
  private boolean nonRecursive = false;
  private boolean alwaysUpdateSnapshots = false;
  private boolean showDialogWithAdvancedSettings = false;
  private boolean useMavenConfig = true;
  private String threads;
  private boolean emulateTerminal = false;

  private MavenExecutionOptions.LoggingLevel outputLevel = MavenExecutionOptions.LoggingLevel.INFO;
  MavenExecutionOptions.ChecksumPolicy checksumPolicy = MavenExecutionOptions.ChecksumPolicy.NOT_SET;
  private MavenExecutionOptions.FailureMode failureBehavior = MavenExecutionOptions.FailureMode.NOT_SET;

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
    changed(true);
  }

  public void changed(boolean fireUpdate) {
    if (myBulkUpdateLevel > 0) return;

    mavenConfigCache = null;
    if (fireUpdate) {
      fireChanged();
    }
  }

  @Property
  public @NotNull MavenExecutionOptions.ChecksumPolicy getChecksumPolicy() {
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
  public @NotNull MavenExecutionOptions.FailureMode getFailureBehavior() {
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
  @Deprecated(forRemoval = true)
  public @NotNull MavenExecutionOptions.LoggingLevel getLoggingLevel() {
    return getOutputLevel();
  }

  @Property
  public @NotNull MavenExecutionOptions.LoggingLevel getOutputLevel() {
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
      changed(false);
    }
  }

  /**
   * @deprecated This method mix paths to maven home and labels like "Use bundled maven" and should be avoided
   * use {@link #getMavenHomeType getMavenHomeType} instead
   */
  //to be removed in IDEA-338870
  @Deprecated(forRemoval = true)
  @ApiStatus.ScheduledForRemoval
  public @Nullable String getMavenHome() {
    if (myForPersistence) {
      return DEFAULT_MAVEN.getTitle(); //avoid saving data for this deprecated field
    }
    return mavenHomeType.getTitle();
  }

  /**
   * @deprecated This method mix paths to maven home and labels like "Use bundled maven" and should be avoided
   * use {@link #setMavenHomeType setMavenHomeType} instead
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.ScheduledForRemoval
  //to be removed in IDEA-338870
  public void setMavenHome(final @NotNull String mavenHome) {
    //noinspection HardCodedStringLiteral
    setMavenHome(resolveMavenHomeType(mavenHome), true);
  }


  @Transient
  public @NotNull MavenHomeType getMavenHomeType() {
    MavenHomeType type = mavenHomeType;
    if (type != null) return type;
    return BundledMaven3.INSTANCE;
  }

  public void setMavenHomeType(final @NotNull MavenHomeType mavenHome) {
    setMavenHome(mavenHome, true);
  }

  @TestOnly
  public void setMavenHomeNoFire(final @NotNull MavenHomeType mavenHome) {
    setMavenHome(mavenHome, false);
  }

  private void setMavenHome(final @NotNull MavenHomeType mavenHome, boolean fireChanged) {
    MavenHomeType mavenHomeToSet = mavenHome;
    if (!Objects.equals(this.mavenHomeType, mavenHomeToSet)) {
      this.mavenHomeType = mavenHomeToSet;
      if (fireChanged) {
        changed();
      }
    }
  }

  public @NotNull String getUserSettingsFile() {
    return mavenSettingsFile;
  }

  public void setUserSettingsFile(@Nullable String mavenSettingsFile) {
    if (mavenSettingsFile == null) return;

    if (!Objects.equals(this.mavenSettingsFile, mavenSettingsFile)) {
      this.mavenSettingsFile = mavenSettingsFile;
      changed();
    }
  }

  /**
   * Do not use this variable.
   * Use MavenSettingsCache.getEffectiveUserLocalRepo instead
   * @return local repository string. This string should not be used to create a file directly
   */
  @ApiStatus.Internal
  public @NotNull String getLocalRepository() {
    return overriddenLocalRepository;
  }

  public void setLocalRepository(final @Nullable String overriddenLocalRepository) {
    if (overriddenLocalRepository == null) return;

    if (!Objects.equals(this.overriddenLocalRepository, overriddenLocalRepository)) {
      this.overriddenLocalRepository = overriddenLocalRepository;
      if (myProject != null) {
        MavenUtil.shutdownMavenConnectors(myProject);
      }
      changed();
    }
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

  public void setAlwaysUpdateSnapshots(boolean value) {
    if (!Comparing.equal(this.alwaysUpdateSnapshots, value)) {
      this.alwaysUpdateSnapshots = value;
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

  public @Nullable String getThreads() {
    return threads;
  }

  public void setThreads(@Nullable String value) {
    String nullizeValue = StringUtil.nullize(value);
    if (!Objects.equals(this.threads, nullizeValue)) {
      this.threads = nullizeValue;
      changed();
    }
  }

  public boolean isEmulateTerminal() {
    return emulateTerminal;
  }

  public void setEmulateTerminal(boolean emulateTerminal) {
    if (!Comparing.equal(this.emulateTerminal, emulateTerminal)) {
      this.emulateTerminal = emulateTerminal;
      changed();
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenGeneralSettings that = (MavenGeneralSettings)o;

    if (nonRecursive != that.nonRecursive) return false;
    if (outputLevel != that.outputLevel) return false;
    if (alwaysUpdateSnapshots != that.alwaysUpdateSnapshots) return false;
    if (showDialogWithAdvancedSettings != that.showDialogWithAdvancedSettings) return false;
    if (printErrorStackTraces != that.printErrorStackTraces) return false;
    if (useMavenConfig != that.useMavenConfig) return false;
    if (workOffline != that.workOffline) return false;
    if (!checksumPolicy.equals(that.checksumPolicy)) return false;
    if (!failureBehavior.equals(that.failureBehavior)) return false;
    if (!overriddenLocalRepository.equals(that.overriddenLocalRepository)) return false;
    if (!mavenHomeType.equals(that.mavenHomeType)) return false;
    if (!mavenSettingsFile.equals(that.mavenSettingsFile)) return false;
    if (!Objects.equals(threads, that.threads)) return false;
    if (emulateTerminal != that.emulateTerminal) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = (workOffline ? 1 : 0);
    result = 31 * result + mavenHomeType.hashCode();
    result = 31 * result + mavenSettingsFile.hashCode();
    result = 31 * result + overriddenLocalRepository.hashCode();
    result = 31 * result + (printErrorStackTraces ? 1 : 0);
    result = 31 * result + (useMavenConfig ? 1 : 0);
    result = 31 * result + (nonRecursive ? 1 : 0);
    result = 31 * result + outputLevel.hashCode();
    result = 31 * result + checksumPolicy.hashCode();
    result = 31 * result + failureBehavior.hashCode();
    result = 31 * result + (emulateTerminal ? 1 : 0);
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
      changed(false);
    }
    mavenConfigCache = config;
  }

  @Transient
  public MavenConfig getMavenConfig() {
    if (!useMavenConfig) return null;
    if (mavenConfigCache != null) return mavenConfigCache;

    MavenProjectsManager instance = myProject != null ? MavenProjectsManager.getInstance(myProject) : null;
    if (instance == null) return null;

    var files = MavenUtil.collectFiles(instance.getRootProjects());
    if (files.isEmpty()) {
      files = instance.getProjectsTree().getExistingManagedFiles();
    }

    updateFromMavenConfig(files);
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


  //used to properly save maven home
  //IDEA-338796
  @ApiStatus.Internal

  public enum MavenHomeTypeForPersistence {
    WRAPPER, BUNDLED3, BUNDLED4, CUSTOM
  }

  @ApiStatus.Internal
  //need for proper persistance of the component, do not use in application code
  public MavenHomeTypeForPersistence getMavenHomeTypeForPersistence() {
    if (mavenHomeType instanceof MavenWrapper) {
      return MavenHomeTypeForPersistence.WRAPPER;
    }
    else if (mavenHomeType instanceof BundledMaven3) {
      return MavenHomeTypeForPersistence.BUNDLED3;
    }
    else if (mavenHomeType instanceof BundledMaven4) {
      return MavenHomeTypeForPersistence.BUNDLED4;
    }
    else {
      return MavenHomeTypeForPersistence.CUSTOM;
    }
  }

  @ApiStatus.Internal
  //need for proper persistance of the component, do not use in application code
  public void setMavenHomeTypeForPersistence(MavenHomeTypeForPersistence value) {
    switch (value) {
      case WRAPPER -> {
        setMavenHomeType(MavenWrapper.INSTANCE);
      }
      case BUNDLED3 -> {
        setMavenHomeType(BundledMaven3.INSTANCE);
      }
      case BUNDLED4 -> {
        setMavenHomeType(BundledMaven4.INSTANCE);
      }
      case CUSTOM -> {
        //do nothing, wait for setCustomMavenHome to be executed
      }
    }
  }

  @ApiStatus.Internal
  //need for proper persistance of the component, do not use in application code
  public String getCustomMavenHome() {
    if (mavenHomeType instanceof MavenInSpecificPath m) {
      return m.getMavenHome();
    }
    return null;
  }

  @ApiStatus.Internal
  //need for proper persistance of the component, do not use in application code
  public void setCustomMavenHome(String custom) {
    if (custom != null) {
      setMavenHomeType(new MavenInSpecificPath(custom));
    }
  }

  @ApiStatus.Internal
  MavenGeneralSettings cloneForPersistence() {
    MavenGeneralSettings clone = this.clone();
    clone.myForPersistence = true;
    return clone;
  }
}
