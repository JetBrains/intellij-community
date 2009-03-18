package org.jetbrains.idea.maven.project;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Kaznacheev
 */
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
  private boolean autoSync = false;
  private boolean createModulesForAggregators = true;
  private boolean createModuleGroups = false;
  private boolean useMavenOutput = true;
  private boolean updateFoldersOnImport = true;
  private String updateFoldersOnImportPhase = UPDATE_FOLDERS_DEFAULT_PHASE;
  private boolean resolveInBackground = true;

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

  public boolean isAutoSync() {
    return autoSync;
  }

  public void setAutoSync(boolean autoSync) {
    this.autoSync = autoSync;
  }

  public boolean isCreateModuleGroups() {
    return createModuleGroups;
  }

  public void setCreateModuleGroups(boolean createModuleGroups) {
    this.createModuleGroups = createModuleGroups;
  }

  public boolean isCreateModulesForAggregators() {
    return createModulesForAggregators;
  }

  public void setCreateModulesForAggregators(boolean createModulesForAggregators) {
    this.createModulesForAggregators = createModulesForAggregators;
  }

  public boolean isUseMavenOutput() {
    return useMavenOutput;
  }

  public void setUseMavenOutput(boolean useMavenOutput) {
    this.useMavenOutput = useMavenOutput;
  }

  public boolean isUpdateFoldersOnImport() {
    return updateFoldersOnImport;
  }

  public void setUpdateFoldersOnImport(boolean updateFoldersOnImport) {
    this.updateFoldersOnImport = updateFoldersOnImport;
  }

  public String getUpdateFoldersOnImportPhase() {
    return updateFoldersOnImportPhase;
  }

  public void setUpdateFoldersOnImportPhase(String updateFoldersOnImportPhase) {
    this.updateFoldersOnImportPhase = updateFoldersOnImportPhase;
  }

  public boolean isResolveInBackground() {
    return resolveInBackground;
  }

  public void setResolveInBackground(boolean value) {
    resolveInBackground = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenImportingSettings that = (MavenImportingSettings)o;

    if (autoSync != that.autoSync) return false;
    if (createModuleGroups != that.createModuleGroups) return false;
    if (createModulesForAggregators != that.createModulesForAggregators) return false;
    if (lookForNested != that.lookForNested) return false;
    if (resolveInBackground != that.resolveInBackground) return false;
    if (updateFoldersOnImport != that.updateFoldersOnImport) return false;
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
    result = 31 * result + (autoSync ? 1 : 0);
    result = 31 * result + (createModulesForAggregators ? 1 : 0);
    result = 31 * result + (createModuleGroups ? 1 : 0);
    result = 31 * result + (useMavenOutput ? 1 : 0);
    result = 31 * result + (updateFoldersOnImport ? 1 : 0);
    result = 31 * result + (updateFoldersOnImportPhase != null ? updateFoldersOnImportPhase.hashCode() : 0);
    result = 31 * result + (resolveInBackground ? 1 : 0);
    return result;
  }

  @Override
  public MavenImportingSettings clone() {
    try {
      return (MavenImportingSettings)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }
}
