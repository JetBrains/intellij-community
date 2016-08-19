/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MavenGeneralSettings implements Cloneable {
  private boolean workOffline = false;
  private String mavenHome = "";
  private String mavenSettingsFile = "";
  private String overriddenLocalRepository = "";
  private boolean printErrorStackTraces = false;
  private boolean usePluginRegistry = false;
  private boolean nonRecursive = false;

  private boolean alwaysUpdateSnapshots = false;

  private String threads;

  private MavenExecutionOptions.LoggingLevel outputLevel = MavenExecutionOptions.LoggingLevel.INFO;
  private MavenExecutionOptions.ChecksumPolicy checksumPolicy = MavenExecutionOptions.ChecksumPolicy.NOT_SET;
  private MavenExecutionOptions.FailureMode failureBehavior = MavenExecutionOptions.FailureMode.NOT_SET;
  private MavenExecutionOptions.PluginUpdatePolicy pluginUpdatePolicy = MavenExecutionOptions.PluginUpdatePolicy.DEFAULT;

  private File myEffectiveLocalRepositoryCache;
  private Set<String> myDefaultPluginsCache;

  private int myBulkUpdateLevel = 0;
  private List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

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
    fireChanged();
  }

  @Property
  @NotNull
  public MavenExecutionOptions.PluginUpdatePolicy getPluginUpdatePolicy() {
    return pluginUpdatePolicy;
  }

  public void setPluginUpdatePolicy(MavenExecutionOptions.PluginUpdatePolicy value) {
    if (value == null) return; // null may come from deserializator
    this.pluginUpdatePolicy = value;
    changed();
  }

  @Property
  @NotNull
  public MavenExecutionOptions.ChecksumPolicy getChecksumPolicy() {
    return checksumPolicy;
  }

  public void setChecksumPolicy(MavenExecutionOptions.ChecksumPolicy value) {
    if (value == null) return; // null may come from deserializator
    this.checksumPolicy = value;
    changed();
  }

  @Property
  @NotNull
  public MavenExecutionOptions.FailureMode getFailureBehavior() {
    return failureBehavior;
  }

  public void setFailureBehavior(MavenExecutionOptions.FailureMode value) {
    if (value == null) return; // null may come from deserializator
    this.failureBehavior = value;
    changed();
  }

  @Transient
  @NotNull
  @Deprecated // Use getOutputLevel()
  public MavenExecutionOptions.LoggingLevel getLoggingLevel() {
    return getOutputLevel();
  }

  @Property
  @NotNull
  public MavenExecutionOptions.LoggingLevel getOutputLevel() {
    return outputLevel;
  }

  public void setOutputLevel(MavenExecutionOptions.LoggingLevel value) {
    if (value == null) return; // null may come from deserializator
    if (!Comparing.equal(this.outputLevel, value)) {
      MavenServerManager.getInstance().setLoggingLevel(value);
      this.outputLevel = value;
      changed();
    }
  }

  public boolean isWorkOffline() {
    return workOffline;
  }

  public void setWorkOffline(boolean workOffline) {
    this.workOffline = workOffline;
    changed();
  }

  @NotNull
  public String getMavenHome() {
    return mavenHome;
  }

  public void setMavenHome(@NotNull final String mavenHome) {
    if (!Comparing.equal(this.mavenHome, mavenHome)) {
      this.mavenHome = mavenHome;
      MavenServerManager.getInstance().setMavenHome(mavenHome);
      myDefaultPluginsCache = null;
      changed();
    }
  }

  @Nullable
  public File getEffectiveMavenHome() {
    return MavenUtil.resolveMavenHomeDirectory(getMavenHome());
  }

  @NotNull
  public String getUserSettingsFile() {
    return mavenSettingsFile;
  }

  public void setUserSettingsFile(@Nullable String mavenSettingsFile) {
    if (mavenSettingsFile == null) return;

    if (!Comparing.equal(this.mavenSettingsFile, mavenSettingsFile)) {
      this.mavenSettingsFile = mavenSettingsFile;
      changed();
    }
  }

  @Nullable
  public File getEffectiveUserSettingsIoFile() {
    return MavenUtil.resolveUserSettingsFile(getUserSettingsFile());
  }

  @Nullable
  public File getEffectiveGlobalSettingsIoFile() {
    return MavenUtil.resolveGlobalSettingsFile(getMavenHome());
  }

  @Nullable
  public VirtualFile getEffectiveUserSettingsFile() {
    File file = getEffectiveUserSettingsIoFile();
    return file == null ? null : LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  public List<VirtualFile> getEffectiveSettingsFiles() {
    List<VirtualFile> result = new ArrayList<>(2);
    VirtualFile file = getEffectiveUserSettingsFile();
    if (file != null) result.add(file);
    file = getEffectiveGlobalSettingsFile();
    if (file != null) result.add(file);
    return result;
  }

  @Nullable
  public VirtualFile getEffectiveGlobalSettingsFile() {
    File file = getEffectiveGlobalSettingsIoFile();
    return file == null ? null : LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  @NotNull
  public String getLocalRepository() {
    return overriddenLocalRepository;
  }

  public void setLocalRepository(final @Nullable String overridenLocalRepository) {
    if (overridenLocalRepository == null) return;

    if (!Comparing.equal(this.overriddenLocalRepository, overridenLocalRepository)) {
      this.overriddenLocalRepository = overridenLocalRepository;
      MavenServerManager.getInstance().shutdown(true);
      changed();
    }
  }

  public File getEffectiveLocalRepository() {
    File result = myEffectiveLocalRepositoryCache;
    if (result != null) return result;

    result = MavenUtil.resolveLocalRepository(overriddenLocalRepository, mavenHome, mavenSettingsFile);
    myEffectiveLocalRepositoryCache = result;
    return result;
  }

  @Nullable
  public VirtualFile getEffectiveSuperPom() {
    return MavenUtil.resolveSuperPomFile(getEffectiveMavenHome());
  }

  @SuppressWarnings("unused")
  public boolean isDefaultPlugin(String groupId, String artifactId) {
    return getDefaultPlugins().contains(groupId + ":" + artifactId);
  }

  private Set<String> getDefaultPlugins() {
    Set<String> result = myDefaultPluginsCache;
    if (result != null) return result;

    result = new THashSet<>();

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
    printErrorStackTraces = value;
    changed();
  }

  public boolean isUsePluginRegistry() {
    return usePluginRegistry;
  }

  public void setUsePluginRegistry(final boolean usePluginRegistry) {
    this.usePluginRegistry = usePluginRegistry;
    changed();
  }

  public boolean isAlwaysUpdateSnapshots() {
    return alwaysUpdateSnapshots;
  }

  public void setAlwaysUpdateSnapshots(boolean alwaysUpdateSnapshots) {
    this.alwaysUpdateSnapshots = alwaysUpdateSnapshots;
    changed();
  }

  public boolean isNonRecursive() {
    return nonRecursive;
  }

  public void setNonRecursive(final boolean nonRecursive) {
    this.nonRecursive = nonRecursive;
    changed();
  }

  @Nullable
  public String getThreads() {
    return threads;
  }

  public void setThreads(@Nullable String threads) {
    this.threads = StringUtil.nullize(threads);
    changed();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenGeneralSettings that = (MavenGeneralSettings)o;

    if (nonRecursive != that.nonRecursive) return false;
    if (outputLevel != that.outputLevel) return false;
    if (pluginUpdatePolicy != that.pluginUpdatePolicy) return false;
    if (alwaysUpdateSnapshots != that.alwaysUpdateSnapshots) return false;
    if (printErrorStackTraces != that.printErrorStackTraces) return false;
    if (usePluginRegistry != that.usePluginRegistry) return false;
    if (workOffline != that.workOffline) return false;
    if (!checksumPolicy.equals(that.checksumPolicy)) return false;
    if (!failureBehavior.equals(that.failureBehavior)) return false;
    if (!overriddenLocalRepository.equals(that.overriddenLocalRepository)) return false;
    if (!mavenHome.equals(that.mavenHome)) return false;
    if (!mavenSettingsFile.equals(that.mavenSettingsFile)) return false;
    if (!Comparing.equal(threads, that.threads)) return false;

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
      return result;
    }
    catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }

  public void addListener(Listener l) {
    myListeners.add(l);
  }

  public void removeListener(Listener l) {
    myListeners.remove(l);
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
