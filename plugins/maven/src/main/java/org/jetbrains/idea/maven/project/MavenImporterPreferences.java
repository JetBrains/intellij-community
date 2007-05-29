package org.jetbrains.idea.maven.project;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenImporterPreferences implements Cloneable {

  private boolean autoImportNew = true;
  @NotNull private String dedicatedModuleDir = "";
  private boolean lookForNested = false;
  private boolean createModuleGroups = false;

  private boolean synchronizeOnStart = false;
  private boolean downloadSources = false;
  private boolean downloadJavadoc = false;
  private boolean generateSources = false;

  public boolean isAutoImportNew() {
    return autoImportNew;
  }

  public void setAutoImportNew(final boolean autoImportNew) {
    this.autoImportNew = autoImportNew;
  }

  @NotNull
  public String getDedicatedModuleDir() {
    return dedicatedModuleDir;
  }

  public void setDedicatedModuleDir(@NotNull final String dedicatedModuleDir) {
    this.dedicatedModuleDir = dedicatedModuleDir;
  }

  public boolean isLookForNested() {
    return lookForNested;
  }

  public void setLookForNested(final boolean lookForNested) {
    this.lookForNested = lookForNested;
  }

  public boolean isCreateModuleGroups() {
    return createModuleGroups;
  }

  public void setCreateModuleGroups(final boolean createModuleGroups) {
    this.createModuleGroups = createModuleGroups;
  }

  public boolean isSynchronizeOnStart() {
    return synchronizeOnStart;
  }

  public void setSynchronizeOnStart(final boolean synchronizeOnStart) {
    this.synchronizeOnStart = synchronizeOnStart;
  }

  public boolean isDownloadSources() {
    return downloadSources;
  }

  public void setDownloadSources(final boolean downloadSources) {
    this.downloadSources = downloadSources;
  }

  public boolean isDownloadJavadoc() {
    return downloadJavadoc;
  }

  public void setDownloadJavadoc(final boolean downloadJavadoc) {
    this.downloadJavadoc = downloadJavadoc;
  }

  public boolean isGenerateSources() {
    return generateSources;
  }

  public void setGenerateSources(final boolean generateSources) {
    this.generateSources = generateSources;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenImporterPreferences that = (MavenImporterPreferences)o;

    if (autoImportNew != that.autoImportNew) return false;
    if (createModuleGroups != that.createModuleGroups) return false;
    if (downloadJavadoc != that.downloadJavadoc) return false;
    if (downloadSources != that.downloadSources) return false;
    if (generateSources != that.generateSources) return false;
    if (lookForNested != that.lookForNested) return false;
    if (synchronizeOnStart != that.synchronizeOnStart) return false;
    //noinspection RedundantIfStatement
    if (!dedicatedModuleDir.equals(that.dedicatedModuleDir)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (autoImportNew ? 1 : 0);
    result = 31 * result + dedicatedModuleDir.hashCode();
    result = 31 * result + (lookForNested ? 1 : 0);
    result = 31 * result + (createModuleGroups ? 1 : 0);
    result = 31 * result + (synchronizeOnStart ? 1 : 0);
    result = 31 * result + (downloadSources ? 1 : 0);
    result = 31 * result + (downloadJavadoc ? 1 : 0);
    result = 31 * result + (generateSources ? 1 : 0);
    return result;
  }

  public MavenImporterPreferences safeClone() {
    try {
      return (MavenImporterPreferences)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }
}
