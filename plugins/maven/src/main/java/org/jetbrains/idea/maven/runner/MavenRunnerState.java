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


package org.jetbrains.idea.maven.runner;

import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MavenRunnerState implements Cloneable {

  @NonNls public static final String USE_INTERNAL_JAVA = "#JAVA_INTERNAL";
  @NonNls public static final String USE_JAVA_HOME = "#JAVA_HOME";

  private boolean runMavenInBackground = false;
  private boolean syncAfterBuild = true;
  private boolean useMavenEmbedder = true;
  @NotNull private String jreName = "";
  @NotNull private String vmOptions = "";
  private boolean skipTests = false;
  private Map<String, String> mavenProperties = new LinkedHashMap<String, String>();

  public boolean isRunMavenInBackground() {
    return runMavenInBackground;
  }

  public void setRunMavenInBackground(boolean runMavenInBackground) {
    this.runMavenInBackground = runMavenInBackground;
  }

  public boolean isSyncAfterBuild() {
    return syncAfterBuild;
  }

  public void setSyncAfterBuild(boolean syncAfterBuild) {
    this.syncAfterBuild = syncAfterBuild;
  }

  public boolean isUseMavenEmbedder() {
    return useMavenEmbedder;
  }

  public void setUseMavenEmbedder(boolean useMavenEmbedder) {
    this.useMavenEmbedder = useMavenEmbedder;
  }

  @NotNull
  public String getJreName() {
    return this.jreName;
  }

  public void setJreName(@Nullable String jdkPath) {
    if (jdkPath != null) {
      this.jreName = jdkPath;
    }
  }

  public void useExternalMaven() {
    setUseMavenEmbedder(false);
    if (StringUtil.isEmpty(jreName)) {
      jreName = collectJdkNamesAndDescriptions().get(0).getFirst();
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
    this.skipTests = skipTests;
  }

  public Map<String, String> getMavenProperties() {
    return this.mavenProperties;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public void setMavenProperties(Map<String, String> mavenProperties) {
    this.mavenProperties = mavenProperties;
  }

  public List<Pair<String, String>> collectJdkNamesAndDescriptions() {
    List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();

    for (ProjectJdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      String name = projectJdk.getName();
      result.add(new Pair<String, String>(name, name));
    }

    result.add(new Pair<String, String>(USE_INTERNAL_JAVA, RunnerBundle.message("maven.java.internal")));
    result.add(new Pair<String, String>(USE_JAVA_HOME, RunnerBundle.message("maven.java.home.env")));

    return result;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenRunnerState that = (MavenRunnerState)o;

    if (runMavenInBackground != that.runMavenInBackground) return false;
    if (skipTests != that.skipTests) return false;
    if (syncAfterBuild != that.syncAfterBuild) return false;
    if (useMavenEmbedder != that.useMavenEmbedder) return false;
    if (!jreName.equals(that.jreName)) return false;
    if (mavenProperties != null ? !mavenProperties.equals(that.mavenProperties) : that.mavenProperties != null) return false;
    if (!vmOptions.equals(that.vmOptions)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (runMavenInBackground ? 1 : 0);
    result = 31 * result + (syncAfterBuild ? 1 : 0);
    result = 31 * result + (useMavenEmbedder ? 1 : 0);
    result = 31 * result + jreName.hashCode();
    result = 31 * result + vmOptions.hashCode();
    result = 31 * result + (skipTests ? 1 : 0);
    result = 31 * result + (mavenProperties != null ? mavenProperties.hashCode() : 0);
    return result;
  }

  @Override
  public MavenRunnerState clone() {
    try {
      final MavenRunnerState clone = (MavenRunnerState)super.clone();
      clone.mavenProperties = cloneMap(mavenProperties);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }

  private static <K,V> Map<K,V> cloneMap(final Map<K, V> source) {
    final Map<K, V> clone = new LinkedHashMap<K, V>();
    for (Map.Entry<K,V> entry : source.entrySet()) {
      clone.put(entry.getKey(), entry.getValue());
    }
    return clone;
  }
}
