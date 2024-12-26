// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;

import java.util.LinkedHashSet;
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

  private @NotNull @NlsSafe String dedicatedModuleDir = "";
  private boolean lookForNested = false;

  private boolean importAutomatically = false;
  private boolean excludeTargetFolder = true;
  private boolean useMavenOutput = true;
  private @NlsSafe String updateFoldersOnImportPhase = UPDATE_FOLDERS_DEFAULT_PHASE;

  private boolean downloadSourcesAutomatically = false;
  private boolean downloadDocsAutomatically = false;
  private boolean downloadAnnotationsAutomatically = false;
  private boolean autoDetectCompiler = true;

  private GeneratedSourcesFolder generatedSourcesFolder = GeneratedSourcesFolder.AUTODETECT;

  private String dependencyTypes = DEFAULT_DEPENDENCY_TYPES;
  private Set<String> myDependencyTypesAsSet;

  private @NotNull @NlsSafe String vmOptionsForImporter = "";

  private @NotNull @NlsSafe String jdkForImporter = MavenRunnerSettings.USE_PROJECT_JDK;

  public enum GeneratedSourcesFolder {
    IGNORE("maven.settings.generated.folder.ignore"),
    AUTODETECT("maven.settings.generated.folder.autodetect"),
    GENERATED_SOURCE_FOLDER("maven.settings.generated.folder.targerdir"),
    SUBFOLDER("maven.settings.generated.folder.targersubdir");

    public final String myMessageKey;

    GeneratedSourcesFolder(String messageKey) {
      myMessageKey = messageKey;
    }

    public @NlsContexts.ListItem String getTitle() {
      return MavenConfigurableBundle.message(myMessageKey);
    }
  }

  // remains for settings backward compatibility until Workspace import is a default option
  @Deprecated
  @ApiStatus.Internal
  public @NotNull @NlsSafe String getDedicatedModuleDir() {
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

  public @NotNull String getDependencyTypes() {
    return dependencyTypes;
  }

  public void setDependencyTypes(@NotNull String dependencyTypes) {
    this.dependencyTypes = dependencyTypes;
    myDependencyTypesAsSet = null;
  }

  public @NotNull Set<String> getDependencyTypesAsSet() {
    if (myDependencyTypesAsSet == null) {
      Set<String> res = new LinkedHashSet<>();

      for (String type : StringUtil.tokenize(dependencyTypes, " \n\r\t,;")) {
        res.add(type);
      }

      myDependencyTypesAsSet = res;
    }
    return myDependencyTypesAsSet;
  }

 /**
   * @deprecated source folders are always kept
   */
  @Deprecated(forRemoval = true)
  public boolean isKeepSourceFolders() {
    return true;
  }

  /**
   * @deprecated source folders are always kept
   */
  @Deprecated(forRemoval = true)
  public void setKeepSourceFolders(boolean keepSourceFolders) {
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

  public @NlsSafe String getUpdateFoldersOnImportPhase() {
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
  public @NotNull GeneratedSourcesFolder getGeneratedSourcesFolder() {
    return generatedSourcesFolder;
  }

  public void setGeneratedSourcesFolder(GeneratedSourcesFolder generatedSourcesFolder) {
    if (generatedSourcesFolder == null) return; // null may come from deserializator

    this.generatedSourcesFolder = generatedSourcesFolder;
  }

  public @NotNull String getVmOptionsForImporter() {
    return vmOptionsForImporter;
  }

  public void setVmOptionsForImporter(String vmOptionsForImporter) {
    this.vmOptionsForImporter = StringUtil.notNullize(vmOptionsForImporter);
  }

  public @NotNull String getJdkForImporter() {
    return jdkForImporter;
  }

  public void setJdkForImporter(@NotNull String jdkForImporter) {
    this.jdkForImporter = jdkForImporter;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenImportingSettings that = (MavenImportingSettings)o;

    if (!dependencyTypes.equals(that.dependencyTypes)) return false;
    if (downloadDocsAutomatically != that.downloadDocsAutomatically) return false;
    if (downloadSourcesAutomatically != that.downloadSourcesAutomatically) return false;
    if (downloadAnnotationsAutomatically != that.downloadAnnotationsAutomatically) return false;
    if (autoDetectCompiler != that.autoDetectCompiler) return false;
    //if (lookForNested != that.lookForNested) return false;
    if (excludeTargetFolder != that.excludeTargetFolder) return false;
    if (useMavenOutput != that.useMavenOutput) return false;
    if (generatedSourcesFolder != that.generatedSourcesFolder) return false;
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
    result = 31 * result + generatedSourcesFolder.hashCode();
    result = 31 * result + dependencyTypes.hashCode();

    return result;
  }

  @Override
  public MavenImportingSettings clone() {
    try {
      MavenImportingSettings result = (MavenImportingSettings)super.clone();
      return result;
    }
    catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }
}
