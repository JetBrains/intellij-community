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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MavenImportingSettings implements Cloneable {

  private static final Logger LOG = Logger.getInstance(MavenImportingSettings.class);

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
  public static final String DEFAULT_DEPENDENCY_TYPES =
    "jar, test-jar, maven-plugin, ejb, ejb-client, jboss-har, jboss-sar, war, ear, bundle";

  private boolean useWorkspaceImport = true;

  @NotNull @NlsSafe private String dedicatedModuleDir = "";
  private boolean lookForNested = false;

  private boolean importAutomatically = false;
  private boolean createModulesForAggregators = true;
  private boolean excludeTargetFolder = true;
  private boolean keepSourceFolders = true;
  private boolean useMavenOutput = true;
  @NlsSafe private String updateFoldersOnImportPhase = UPDATE_FOLDERS_DEFAULT_PHASE;

  private boolean downloadSourcesAutomatically = false;
  private boolean downloadDocsAutomatically = false;
  private boolean downloadAnnotationsAutomatically = false;
  private boolean autoDetectCompiler = true;

  private GeneratedSourcesFolder generatedSourcesFolder = GeneratedSourcesFolder.AUTODETECT;

  private String dependencyTypes = DEFAULT_DEPENDENCY_TYPES;
  private Set<String> myDependencyTypesAsSet;

  @NotNull @NlsSafe private String vmOptionsForImporter = "";

  @NotNull @NlsSafe private String jdkForImporter = MavenRunnerSettings.USE_PROJECT_JDK;

  private List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean workspaceImportForciblyTurnedOn = false;

  @ApiStatus.Internal
  public boolean isWorkspaceImportForciblyTurnedOn() {
    return workspaceImportForciblyTurnedOn;
  }

  @ApiStatus.Internal
  public void setWorkspaceImportForciblyTurnedOn(boolean workspaceImportForciblyTurnedOn) {
    this.workspaceImportForciblyTurnedOn = workspaceImportForciblyTurnedOn;
  }

  public enum GeneratedSourcesFolder {
    IGNORE("maven.settings.generated.folder.ignore"),
    AUTODETECT("maven.settings.generated.folder.autodetect"),
    GENERATED_SOURCE_FOLDER("maven.settings.generated.folder.targerdir"),
    SUBFOLDER("maven.settings.generated.folder.targersubdir");

    public final String myMessageKey;

    GeneratedSourcesFolder(String messageKey) {
      myMessageKey = messageKey;
    }

    @NlsContexts.ListItem
    public String getTitle() {
      return MavenConfigurableBundle.message(myMessageKey);
    }
  }

  @Deprecated
  @ApiStatus.Internal // remains for settings backward compatibility until Workspace import is a default option
  @NotNull
  @NlsSafe
  public String getDedicatedModuleDir() {
    return dedicatedModuleDir;
  }

  @Deprecated
  @ApiStatus.Internal // remains for settings backward compatibility until Workspace import is a default option
  public void setDedicatedModuleDir(@NotNull String dedicatedModuleDir) {
    this.dedicatedModuleDir = dedicatedModuleDir;
  }

  public boolean isLookForNested() {
    return lookForNested;
  }

  public void setLookForNested(boolean lookForNested) {
    this.lookForNested = lookForNested;
  }

  /**
   * @deprecated see {@link MavenImportingSettings#setImportAutomatically(boolean)} for details
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  public boolean isImportAutomatically() {
    return importAutomatically;
  }

  /**
   * @see com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker for details
   * @deprecated Auto-import cannot be disabled
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  public void setImportAutomatically(@SuppressWarnings("unused") boolean importAutomatically) {
    this.importAutomatically = importAutomatically;
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

  @ApiStatus.Internal
  public boolean isWorkspaceImportEnabled() {
    return useWorkspaceImport || !isNonWorkspaceImportAvailable();
  }

  @ApiStatus.Internal
  public boolean isNonWorkspaceImportAvailable() {
    return Registry.is("maven.legacy.import.available");
  }

  @ApiStatus.Internal
  public void setWorkspaceImportEnabled(boolean enabled) {
    boolean changedValue = useWorkspaceImport != enabled;
    useWorkspaceImport = enabled;

    if (changedValue) {
      fireUpdateAllProjectStructure();
    }
  }

  public boolean isCreateModulesForAggregators() {
    return createModulesForAggregators;
  }

  public void setCreateModulesForAggregators(boolean createModulesForAggregators) {
    boolean changed = this.createModulesForAggregators != createModulesForAggregators;
    this.createModulesForAggregators = createModulesForAggregators;
    if (changed) {
      fireCreateModuleForAggregatorsChanged();
    }
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

  @NlsSafe
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

  public boolean isDownloadAnnotationsAutomatically() {
    return downloadAnnotationsAutomatically;
  }

  public void setDownloadAnnotationsAutomatically(boolean value) {
    this.downloadAnnotationsAutomatically = value;
  }

  public boolean isAutoDetectCompiler() {
    return autoDetectCompiler;
  }

  public void setAutoDetectCompiler(boolean autoDetectCompiler) {
    this.autoDetectCompiler = autoDetectCompiler;
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

  @NotNull
  public String getVmOptionsForImporter() {
    return vmOptionsForImporter;
  }

  public void setVmOptionsForImporter(String vmOptionsForImporter) {
    this.vmOptionsForImporter = StringUtil.notNullize(vmOptionsForImporter);
  }

  @NotNull
  public String getJdkForImporter() {
    return jdkForImporter;
  }

  public void setJdkForImporter(@NotNull String jdkForImporter) {
    this.jdkForImporter = jdkForImporter;
  }


  public void copyListeners(MavenImportingSettings another) {
    myListeners.addAll(another.myListeners);
  }
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenImportingSettings that = (MavenImportingSettings)o;

    if (useWorkspaceImport != that.useWorkspaceImport) return false;
    if (createModulesForAggregators != that.createModulesForAggregators) return false;
    if (!dependencyTypes.equals(that.dependencyTypes)) return false;
    if (downloadDocsAutomatically != that.downloadDocsAutomatically) return false;
    if (downloadSourcesAutomatically != that.downloadSourcesAutomatically) return false;
    if (downloadAnnotationsAutomatically != that.downloadAnnotationsAutomatically) return false;
    if (autoDetectCompiler != that.autoDetectCompiler) return false;
    //if (lookForNested != that.lookForNested) return false;
    if (keepSourceFolders != that.keepSourceFolders) return false;
    if (excludeTargetFolder != that.excludeTargetFolder) return false;
    if (useMavenOutput != that.useMavenOutput) return false;
    if (generatedSourcesFolder != that.generatedSourcesFolder) return false;
    if (!dedicatedModuleDir.equals(that.dedicatedModuleDir)) return false;
    if (!jdkForImporter.equals(that.jdkForImporter)) return false;
    if (!vmOptionsForImporter.equals(that.vmOptionsForImporter)) return false;
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

    //if (lookForNested) result++;
    //result <<= 1;
    if (useWorkspaceImport) result++;
    result <<= 1;
    if (createModulesForAggregators) result++;
    result <<= 1;
    if (keepSourceFolders) result++;
    result <<= 1;
    if (useMavenOutput) result++;
    result <<= 1;
    if (downloadSourcesAutomatically) result++;
    result <<= 1;
    if (downloadDocsAutomatically) result++;
    result <<= 1;
    if (downloadAnnotationsAutomatically) result++;
    result <<= 1;
    if (autoDetectCompiler) result++;
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

  private void fireCreateModuleForAggregatorsChanged() {
    for (Listener each : myListeners) {
      each.createModuleForAggregatorsChanged();
    }
  }

  private void fireUpdateAllProjectStructure() {
    for (Listener each : myListeners) {
      each.updateAllProjectStructure();
    }
  }

  public interface Listener {

    void createModuleForAggregatorsChanged();

    void updateAllProjectStructure();
  }
}
