package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.apache.maven.execution.MavenExecutionRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
@SuppressWarnings({"UnusedDeclaration"})
public class MavenGeneralSettings implements Cloneable {
  private boolean workOffline = false;
  @NotNull private String mavenHome = "";
  @NotNull private String mavenSettingsFile = "";
  @NotNull private String localRepository = "";
  private boolean printErrorStackTraces = false;
  private boolean usePluginRegistry = false;
  private boolean nonRecursive = false;
  private int outputLevel = MavenExecutionRequest.LOGGING_LEVEL_INFO;
  @NotNull private String checksumPolicy = MavenExecutionRequest.CHECKSUM_POLICY_FAIL;
  @NotNull private String failureBehavior = MavenExecutionRequest.REACTOR_FAIL_FAST;
  private boolean pluginUpdatePolicy = false;

  private File myEffectiveLocalRepositoryCache;

  private List<Listener> myListeners = ContainerUtil.createEmptyCOWList();

  public boolean getPluginUpdatePolicy() {
    return pluginUpdatePolicy;
  }

  public void setPluginUpdatePolicy(boolean pluginUpdatePolicy) {
    this.pluginUpdatePolicy = pluginUpdatePolicy;
  }

  @NotNull
  public String getChecksumPolicy() {
    return checksumPolicy;
  }

  public void setChecksumPolicy(@Nullable String checksumPolicy) {
    if (checksumPolicy != null) {
      this.checksumPolicy = checksumPolicy;
    }
  }

  @NotNull
  public String getFailureBehavior() {
    return failureBehavior;
  }

  public void setFailureBehavior(@Nullable String failureBehavior) {
    if (failureBehavior != null) {
      this.failureBehavior = failureBehavior;
    }
  }

  public boolean isWorkOffline() {
    return workOffline;
  }

  public void setWorkOffline(final boolean workOffline) {
    this.workOffline = workOffline;
  }

  public int getOutputLevel() {
    return outputLevel;
  }

  void setOutputLevel(int outputLevel) {
    this.outputLevel = outputLevel;
  }

  @NotNull
  public String getLocalRepository() {
    return localRepository;
  }

  public File getEffectiveLocalRepository() {
    if (myEffectiveLocalRepositoryCache == null) {
      myEffectiveLocalRepositoryCache = MavenEmbedderFactory.resolveLocalRepository(mavenHome, mavenSettingsFile, localRepository);
    }
    return myEffectiveLocalRepositoryCache;
  }

  public void setLocalRepository(final @Nullable String localRepository) {
    if (localRepository != null) {
      if (!Comparing.equal(this.localRepository, localRepository)) {
        this.localRepository = localRepository;

        myEffectiveLocalRepositoryCache = null;
        firePathChanged();
      }
    }
  }

  @NotNull
  public String getMavenHome() {
    return mavenHome;
  }

  public void setMavenHome(@NotNull final String mavenHome) {
    if (!Comparing.equal(this.mavenHome, mavenHome)) {
      this.mavenHome = mavenHome;

      myEffectiveLocalRepositoryCache = null;
      firePathChanged();
    }
  }

  @NotNull
  public String getMavenSettingsFile() {
    return mavenSettingsFile;
  }

  public void setMavenSettingsFile(@Nullable String mavenSettingsFile) {
    if (mavenSettingsFile != null) {
      if (!Comparing.equal(this.mavenSettingsFile, mavenSettingsFile)) {
        this.mavenSettingsFile = mavenSettingsFile;

        myEffectiveLocalRepositoryCache = null;
        firePathChanged();
      }
    }
  }

  @Nullable
  public File getEffectiveUserSettingsIoFile() {
    return MavenEmbedderFactory.resolveUserSettingsFile(getMavenSettingsFile());
  }

  @Nullable
  public File getEffectiveGlobalSettingsIoFile() {
    return MavenEmbedderFactory.resolveGlobalSettingsFile(getMavenHome());
  }

  @Nullable
  public VirtualFile getEffectiveUserSettingsFile() {
    File file = getEffectiveUserSettingsIoFile();
    return file == null ? null : LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  public List<VirtualFile> getEffectiveSettingsFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>(2);
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
  public VirtualFile getEffectiveSuperPom() {
    return MavenEmbedderFactory.resolveSuperPomFile(getMavenHome());
  }

  public boolean isPrintErrorStackTraces() {
    return printErrorStackTraces;
  }

  public void setPrintErrorStackTraces(boolean value) {
    printErrorStackTraces = value;
  }

  public boolean isUsePluginRegistry() {
    return usePluginRegistry;
  }

  public void setUsePluginRegistry(final boolean usePluginRegistry) {
    this.usePluginRegistry = usePluginRegistry;
  }

  public boolean isNonRecursive() {
    return nonRecursive;
  }

  public void setNonRecursive(final boolean nonRecursive) {
    this.nonRecursive = nonRecursive;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenGeneralSettings that = (MavenGeneralSettings)o;

    if (nonRecursive != that.nonRecursive) return false;
    if (outputLevel != that.outputLevel) return false;
    if (pluginUpdatePolicy != that.pluginUpdatePolicy) return false;
    if (printErrorStackTraces != that.printErrorStackTraces) return false;
    if (usePluginRegistry != that.usePluginRegistry) return false;
    if (workOffline != that.workOffline) return false;
    if (!checksumPolicy.equals(that.checksumPolicy)) return false;
    if (!failureBehavior.equals(that.failureBehavior)) return false;
    if (!localRepository.equals(that.localRepository)) return false;
    if (!mavenHome.equals(that.mavenHome)) return false;
    if (!mavenSettingsFile.equals(that.mavenSettingsFile)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (workOffline ? 1 : 0);
    result = 31 * result + mavenHome.hashCode();
    result = 31 * result + mavenSettingsFile.hashCode();
    result = 31 * result + localRepository.hashCode();
    result = 31 * result + (printErrorStackTraces ? 1 : 0);
    result = 31 * result + (usePluginRegistry ? 1 : 0);
    result = 31 * result + (nonRecursive ? 1 : 0);
    result = 31 * result + outputLevel;
    result = 31 * result + checksumPolicy.hashCode();
    result = 31 * result + failureBehavior.hashCode();
    result = 31 * result + (pluginUpdatePolicy ? 1 : 0);
    return result;
  }

  @Override
  public MavenGeneralSettings clone() {
    try {
      MavenGeneralSettings result = (MavenGeneralSettings)super.clone();
      result.myListeners = ContainerUtil.createEmptyCOWList();
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

  private void firePathChanged() {
    for (Listener each : myListeners) {
      each.pathChanged();
    }
  }

  public interface Listener {
    void pathChanged();
  }
}
