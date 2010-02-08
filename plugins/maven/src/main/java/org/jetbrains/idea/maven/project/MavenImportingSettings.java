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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MavenImportingSettings implements Cloneable {
  private static final String PROCESS_RESOURCES_PHASE = "process-resources";
  public static final String[] UPDATE_FOLDERS_PHASES = new String[]{
    "generate-sources",
    "process-sources",
    "generate-resources",
    PROCESS_RESOURCES_PHASE,
    "generate-test-sources",
    "process-test-sources",
    "generate-test-resources",
    "process-test-resources"};
  public static final String UPDATE_FOLDERS_DEFAULT_PHASE = PROCESS_RESOURCES_PHASE;

  @NotNull private String dedicatedModuleDir = "";
  private boolean lookForNested = false;

  private boolean importAutomatically = false;
  private boolean createModulesForAggregators = true;
  private boolean createModuleGroups = false;
  private boolean useMavenOutput = true;
  private String updateFoldersOnImportPhase = UPDATE_FOLDERS_DEFAULT_PHASE;

  private boolean downloadSourcesAutomatically = false;
  private boolean downloadDocsAutomatically = false;

  private List<Listener> myListeners = ContainerUtil.createEmptyCOWList();

  @NotNull
  public String getDedicatedModuleDir() {
    return dedicatedModuleDir;
  }

  public void setDedicatedModuleDir(@NotNull String dedicatedModuleDir) {
    this.dedicatedModuleDir = dedicatedModuleDir;
  }

  public boolean isLookForNested() {
    return lookForNested;
  }

  public void setLookForNested(boolean lookForNested) {
    this.lookForNested = lookForNested;
  }

  public boolean isImportAutomatically() {
    return importAutomatically;
  }

  public void setImportAutomatically(boolean importAutomatically) {
    this.importAutomatically = importAutomatically;
    fireAutoImportChanged();
  }

  public boolean isCreateModuleGroups() {
    return createModuleGroups;
  }

  public void setCreateModuleGroups(boolean createModuleGroups) {
    this.createModuleGroups = createModuleGroups;
    fireCreateModuleGroupsChanged();
  }

  public boolean isCreateModulesForAggregators() {
    return createModulesForAggregators;
  }

  public void setCreateModulesForAggregators(boolean createModulesForAggregators) {
    this.createModulesForAggregators = createModulesForAggregators;
    fireCreateModuleForAggregatorsChanged();
  }

  public boolean isUseMavenOutput() {
    return useMavenOutput;
  }

  public void setUseMavenOutput(boolean useMavenOutput) {
    this.useMavenOutput = useMavenOutput;
  }

  public String getUpdateFoldersOnImportPhase() {
    return updateFoldersOnImportPhase;
  }

  public void setUpdateFoldersOnImportPhase(String updateFoldersOnImportPhase) {
    this.updateFoldersOnImportPhase = updateFoldersOnImportPhase;
  }

  public boolean shouldDownloadSourcesAutomatically() {
    return downloadSourcesAutomatically;
  }

  public void setDownloadSourcesAutomatically(boolean Value) {
    this.downloadSourcesAutomatically = Value;
  }

  public boolean shouldDownloadDocsAutomatically() {
    return downloadDocsAutomatically;
  }

  public void setDownloadDocsAutomatically(boolean value) {
    this.downloadDocsAutomatically = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenImportingSettings that = (MavenImportingSettings)o;

    if (createModuleGroups != that.createModuleGroups) return false;
    if (createModulesForAggregators != that.createModulesForAggregators) return false;
    if (importAutomatically != that.importAutomatically) return false;
    if (downloadDocsAutomatically != that.downloadDocsAutomatically) return false;
    if (downloadSourcesAutomatically != that.downloadSourcesAutomatically) return false;
    if (lookForNested != that.lookForNested) return false;
    if (useMavenOutput != that.useMavenOutput) return false;
    if (!dedicatedModuleDir.equals(that.dedicatedModuleDir)) return false;
    if (updateFoldersOnImportPhase != null
        ? !updateFoldersOnImportPhase.equals(that.updateFoldersOnImportPhase)
        : that.updateFoldersOnImportPhase != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = dedicatedModuleDir.hashCode();
    result = 31 * result + (lookForNested ? 1 : 0);
    result = 31 * result + (importAutomatically ? 1 : 0);
    result = 31 * result + (createModulesForAggregators ? 1 : 0);
    result = 31 * result + (createModuleGroups ? 1 : 0);
    result = 31 * result + (useMavenOutput ? 1 : 0);
    result = 31 * result + (updateFoldersOnImportPhase != null ? updateFoldersOnImportPhase.hashCode() : 0);
    result = 31 * result + (downloadSourcesAutomatically ? 1 : 0);
    result = 31 * result + (downloadDocsAutomatically ? 1 : 0);
    return result;
  }

  @Override
  public MavenImportingSettings clone() {
    try {
      MavenImportingSettings result = (MavenImportingSettings)super.clone();
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

  private void fireAutoImportChanged() {
    for (Listener each : myListeners) {
      each.autoImportChanged();
    }
  }

  private void fireCreateModuleGroupsChanged() {
    for (Listener each : myListeners) {
      each.createModuleGroupsChanged();
    }
  }

  private void fireCreateModuleForAggregatorsChanged() {
    for (Listener each : myListeners) {
      each.createModuleForAggregatorsChanged();
    }
  }

  public interface Listener {
    void autoImportChanged();
    void createModuleGroupsChanged();
    void createModuleForAggregatorsChanged();
  }
}
