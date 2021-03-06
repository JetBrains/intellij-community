// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.DisposableWrapperList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class MavenRunnerSettings implements Cloneable {
  public MavenRunnerSettings() {}

  @NonNls public static final String USE_INTERNAL_JAVA = ExternalSystemJdkUtil.USE_INTERNAL_JAVA;
  @NonNls public static final String USE_PROJECT_JDK = ExternalSystemJdkUtil.USE_PROJECT_JDK;
  @NonNls public static final String USE_JAVA_HOME = ExternalSystemJdkUtil.USE_JAVA_HOME;

  private boolean delegateBuildToMaven = false;
  private boolean runMavenInBackground = true;
  @NotNull private String jreName = USE_PROJECT_JDK;
  @NotNull private String vmOptions = "";
  private boolean skipTests = false;
  private Map<String, String> mavenProperties = new LinkedHashMap<>();

  private Map<String, String> environmentProperties = new HashMap<>();
  private boolean passParentEnv = true;

  private DisposableWrapperList<Listener> myListeners = new DisposableWrapperList<>();

  public boolean isDelegateBuildToMaven() {
    return delegateBuildToMaven;
  }

  public void setDelegateBuildToMaven(boolean delegateBuildToMaven) {
    this.delegateBuildToMaven = delegateBuildToMaven;
  }

  public boolean isRunMavenInBackground() {
    return runMavenInBackground;
  }

  public void setRunMavenInBackground(boolean runMavenInBackground) {
    this.runMavenInBackground = runMavenInBackground;
  }

  @NotNull
  @NlsSafe
  public String getJreName() {
    return jreName;
  }

  /**
   * @param jreName null means set default value
   */
  public void setJreName(@Nullable String jreName) {
    this.jreName = Objects.requireNonNullElse(jreName, USE_PROJECT_JDK);
  }

  @NotNull
  public String getVmOptions() {
    return vmOptions;
  }

  public void setVmOptions(@Nullable String vmOptions) {
    if (vmOptions != null) {
      this.vmOptions = vmOptions;
    }
  }

  public boolean isSkipTests() {
    return skipTests;
  }

  public void setSkipTests(boolean skipTests) {
    if (skipTests != this.skipTests) fireSkipTestsChanged();
    this.skipTests = skipTests;
  }

  public Map<String, String> getMavenProperties() {
    return this.mavenProperties;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public void setMavenProperties(Map<String, String> mavenProperties) {
    this.mavenProperties = mavenProperties;
  }

  @NotNull
  public Map<String, String> getEnvironmentProperties() {
    return environmentProperties;
  }

  public void setEnvironmentProperties(@NotNull Map<String, String> envs) {
    if (envs == environmentProperties) return;

    environmentProperties.clear();
    environmentProperties.putAll(envs);
  }

  public boolean isPassParentEnv() {
    return passParentEnv;
  }

  public void setPassParentEnv(boolean passParentEnv) {
    this.passParentEnv = passParentEnv;
  }

  /**
   * @deprecated use #addListener(Listener, Disposable)
   */
  @Deprecated
  public void addListener(Listener l) {
    myListeners.add(l);
  }

  public void addListener(@NotNull Listener l, @NotNull Disposable disposable) {
    myListeners.add(l, disposable);
  }

  public void removeListener(Listener l) {
    myListeners.remove(l);
  }

  private void fireSkipTestsChanged() {
    for (Listener each : myListeners) {
      each.skipTestsChanged();
    }
  }

  public interface Listener {
    void skipTestsChanged();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenRunnerSettings that = (MavenRunnerSettings)o;

    if (delegateBuildToMaven != that.delegateBuildToMaven) return false;
    if (runMavenInBackground != that.runMavenInBackground) return false;
    if (skipTests != that.skipTests) return false;
    if (!jreName.equals(that.jreName)) return false;
    if (mavenProperties != null ? !mavenProperties.equals(that.mavenProperties) : that.mavenProperties != null) return false;
    if (!vmOptions.equals(that.vmOptions)) return false;
    if (!environmentProperties.equals(that.environmentProperties)) return false;
    if (passParentEnv != that.passParentEnv) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (delegateBuildToMaven ? 1 : 0);
    result = 31 * result + (runMavenInBackground ? 1 : 0);
    result = 31 * result + jreName.hashCode();
    result = 31 * result + vmOptions.hashCode();
    result = 31 * result + (skipTests ? 1 : 0);
    result = 31 * result + environmentProperties.hashCode();
    result = 31 * result + (mavenProperties != null ? mavenProperties.hashCode() : 0);
    return result;
  }

  @Override
  public MavenRunnerSettings clone() {
    try {
      final MavenRunnerSettings clone = (MavenRunnerSettings)super.clone();
      clone.mavenProperties = cloneMap(mavenProperties);
      clone.myListeners = new DisposableWrapperList<>();
      clone.environmentProperties = new HashMap<>(environmentProperties);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }

  private static <K, V> Map<K, V> cloneMap(final Map<K, V> source) {
    final Map<K, V> clone = new LinkedHashMap<>();
    for (Map.Entry<K, V> entry : source.entrySet()) {
      clone.put(entry.getKey(), entry.getValue());
    }
    return clone;
  }
}
