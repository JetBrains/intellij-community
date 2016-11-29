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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
  private boolean excludeTargetFolder = true;
  private boolean keepSourceFolders = true;
  private boolean useMavenOutput = true;
  private String updateFoldersOnImportPhase = UPDATE_FOLDERS_DEFAULT_PHASE;

  private boolean downloadSourcesAutomatically = false;
  private boolean downloadDocsAutomatically = false;

  private GeneratedSourcesFolder generatedSourcesFolder = GeneratedSourcesFolder.AUTODETECT;

  private String dependencyTypes = "jar, test-jar, maven-plugin, ejb, ejb-client, jboss-har, jboss-sar, war, ear, bundle";
  private Set<String> myDependencyTypesAsSet;

  private List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public enum GeneratedSourcesFolder {
    IGNORE("Don't detect"),
    AUTODETECT("Detect automatically"),
    GENERATED_SOURCE_FOLDER("target/generated-sources"),
    SUBFOLDER("subdirectories of \"target/generated-sources\"");

    public final String title;

    GeneratedSourcesFolder(String title) {
      this.title = title;
    }
  }

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

  @NotNull
  public String getDependencyTypes() {
    return dependencyTypes;
  }

  public void setDependencyTypes(@NotNull String dependencyTypes) {
    this.dependencyTypes = dependencyTypes;
    myDependencyTypesAsSet = null;
  }

  @NotNull
  public Set<String> getDependencyTypesAsSet() {
    if (myDependencyTypesAsSet == null) {
      Set<String> res = new LinkedHashSet<>();

      for (String type : StringUtil.tokenize(dependencyTypes, " \n\r\t,;")) {
        res.add(type);
      }

      myDependencyTypesAsSet = res;
    }
    return myDependencyTypesAsSet;
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

  public boolean isKeepSourceFolders() {
    return keepSourceFolders;
  }

  public void setKeepSourceFolders(boolean keepSourceFolders) {
    this.keepSourceFolders = keepSourceFolders;
  }

  public boolean isExcludeTargetFolder() {
    return excludeTargetFolder;
  }

  public void setExcludeTargetFolder(boolean excludeTargetFolder) {
    this.excludeTargetFolder = excludeTargetFolder;
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

  public boolean isDownloadSourcesAutomatically() {
    return downloadSourcesAutomatically;
  }

  public void setDownloadSourcesAutomatically(boolean Value) {
    this.downloadSourcesAutomatically = Value;
  }

  public boolean isDownloadDocsAutomatically() {
    return downloadDocsAutomatically;
  }

  public void setDownloadDocsAutomatically(boolean value) {
    this.downloadDocsAutomatically = value;
  }

  @Property
  @NotNull
  public GeneratedSourcesFolder getGeneratedSourcesFolder() {
    return generatedSourcesFolder;
  }

  public void setGeneratedSourcesFolder(GeneratedSourcesFolder generatedSourcesFolder) {
    if (generatedSourcesFolder == null) return; // null may come from deserializator

    this.generatedSourcesFolder = generatedSourcesFolder;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenImportingSettings that = (MavenImportingSettings)o;

    if (createModuleGroups != that.createModuleGroups) return false;
    if (createModulesForAggregators != that.createModulesForAggregators) return false;
    if (importAutomatically != that.importAutomatically) return false;
    if (!dependencyTypes.equals(that.dependencyTypes)) return false;
    if (downloadDocsAutomatically != that.downloadDocsAutomatically) return false;
    if (downloadSourcesAutomatically != that.downloadSourcesAutomatically) return false;
    if (lookForNested != that.lookForNested) return false;
    if (keepSourceFolders != that.keepSourceFolders) return false;
    if (excludeTargetFolder != that.excludeTargetFolder) return false;
    if (useMavenOutput != that.useMavenOutput) return false;
    if (generatedSourcesFolder != that.generatedSourcesFolder) return false;
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
    int result = 0;

    if (lookForNested) result++;
    result <<= 1;
    if (importAutomatically) result++;
    result <<= 1;
    if (createModulesForAggregators) result++;
    result <<= 1;
    if (createModuleGroups) result++;
    result <<= 1;
    if (keepSourceFolders) result++;
    result <<= 1;
    if (useMavenOutput) result++;
    result <<= 1;
    if (downloadSourcesAutomatically) result++;
    result <<= 1;
    if (downloadDocsAutomatically) result++;
    result <<= 1;

    result = 31 * result + (updateFoldersOnImportPhase != null ? updateFoldersOnImportPhase.hashCode() : 0);
    result = 31 * result + dedicatedModuleDir.hashCode();
    result = 31 * result + generatedSourcesFolder.hashCode();
    result = 31 * result + dependencyTypes.hashCode();

    return result;
  }

  @Override
  public MavenImportingSettings clone() {
    try {
      MavenImportingSettings result = (MavenImportingSettings)super.clone();
      result.myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
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
