/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */


package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MavenRunnerSettings implements Cloneable {

  @NonNls public static final String USE_INTERNAL_JAVA = ExternalSystemJdkUtil.USE_INTERNAL_JAVA;
  @NonNls public static final String USE_PROJECT_JDK = ExternalSystemJdkUtil.USE_PROJECT_JDK;
  @NonNls public static final String USE_JAVA_HOME = ExternalSystemJdkUtil.USE_JAVA_HOME;

  private boolean runMavenInBackground = true;
  @NotNull private String jreName = USE_PROJECT_JDK;
  @NotNull private String vmOptions = "";
  private boolean skipTests = false;
  private Map<String, String> mavenProperties = new LinkedHashMap<>();

  private Map<String, String> environmentProperties = new HashMap<>();
  private boolean passParentEnv = true;

  private List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public boolean isRunMavenInBackground() {
    return runMavenInBackground;
  }

  public void setRunMavenInBackground(boolean runMavenInBackground) {
    this.runMavenInBackground = runMavenInBackground;
  }

  @NotNull
  public String getJreName() {
    return jreName;
  }

  public void setJreName(@Nullable String jreName) {
    if (jreName != null) {
      this.jreName = jreName;
    }
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

  public void addListener(Listener l) {
    myListeners.add(l);
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
    result = (runMavenInBackground ? 1 : 0);
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
      clone.myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
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
