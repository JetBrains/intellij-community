package org.jetbrains.idea.maven.core;

import org.apache.maven.execution.MavenExecutionRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenEnv;

import java.io.File;

/**
 * @author Vladislav.Kaznacheev
 */
@SuppressWarnings({"UnusedDeclaration"})
public class MavenCoreState implements Cloneable {

  private boolean workOffline = false;
  @NotNull private String mavenHome = "";
  @NotNull private String mavenSettingsFile = "";
  @NotNull private String localRepository = "";
  private boolean produceExceptionErrorMessages = false;
  private boolean usePluginRegistry = false;
  private boolean nonRecursive = false;
  private int outputLevel = MavenExecutionRequest.LOGGING_LEVEL_INFO;
  @NotNull private String checksumPolicy = MavenExecutionRequest.CHECKSUM_POLICY_FAIL;
  @NotNull private String failureBehavior = MavenExecutionRequest.REACTOR_FAIL_FAST;
  private boolean pluginUpdatePolicy = false;


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

  public String getOutputLevelString() {
    return String.valueOf(outputLevel);
  }

  public void setOutputLevelString(String outputLevel) {
    try {
      setOutputLevel(Integer.parseInt(outputLevel));
    }
    catch (NumberFormatException ignore) {
    }
  }

  @NotNull
  public String getLocalRepository() {
    return localRepository;
  }

  @Nullable
  public File getEffectiveLocalRepository() {
    return MavenEnv.resolveLocalRepository(mavenHome, mavenSettingsFile, localRepository);
  }

  public void setLocalRepository(final @Nullable String localRepository) {
    if (localRepository != null) {
      this.localRepository = localRepository;
    }
  }

  @NotNull
  public String getMavenHome() {
    return mavenHome;
  }

  public void setMavenHome(@NotNull final String mavenHome) {
    this.mavenHome = mavenHome;
  }

  @NotNull
  public String getMavenSettingsFile() {
    return mavenSettingsFile;
  }

  public void setMavenSettingsFile(@Nullable String mavenSettingsFile) {
    if (mavenSettingsFile != null) {
      this.mavenSettingsFile = mavenSettingsFile;
    }
  }

  public boolean isProduceExceptionErrorMessages() {
    return produceExceptionErrorMessages;
  }

  public void setProduceExceptionErrorMessages(final boolean produceExceptionErrorMessages) {
    this.produceExceptionErrorMessages = produceExceptionErrorMessages;
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

    final MavenCoreState that = (MavenCoreState)o;

    if (nonRecursive != that.nonRecursive) return false;
    if (outputLevel != that.outputLevel) return false;
    if (pluginUpdatePolicy != that.pluginUpdatePolicy) return false;
    if (produceExceptionErrorMessages != that.produceExceptionErrorMessages) return false;
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
    result = 31 * result + (produceExceptionErrorMessages ? 1 : 0);
    result = 31 * result + (usePluginRegistry ? 1 : 0);
    result = 31 * result + (nonRecursive ? 1 : 0);
    result = 31 * result + outputLevel;
    result = 31 * result + checksumPolicy.hashCode();
    result = 31 * result + failureBehavior.hashCode();
    result = 31 * result + (pluginUpdatePolicy ? 1 : 0);
    return result;
  }

  public MavenCoreState cloneSafe() {
    try {
      return (MavenCoreState)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }
}
