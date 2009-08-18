package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.ArtifactEditor;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactEditorContextImpl implements ArtifactEditorContext {
  private final PackagingEditorContext myParent;
  private final ArtifactEditorEx myEditor;

  public ArtifactEditorContextImpl(PackagingEditorContext parent, ArtifactEditorEx editor) {
    myParent = parent;
    myEditor = editor;
  }

  @NotNull
  public ModifiableArtifactModel getModifiableArtifactModel() {
    return myParent.getModifiableArtifactModel();
  }

  @NotNull
  public ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType) {
    return myParent.getManifestFile(element, artifactType);
  }

  @NotNull
  public Project getProject() {
    return myParent.getProject();
  }

  public CompositePackagingElement<?> getRootElement(@NotNull Artifact originalArtifact) {
    return myParent.getRootElement(originalArtifact);
  }

  public void ensureRootIsWritable(@NotNull Artifact originalArtifact) {
    myParent.ensureRootIsWritable(originalArtifact);
  }

  public ArtifactEditor getOrCreateEditor(Artifact artifact) {
    return myParent.getOrCreateEditor(artifact);
  }

  @NotNull
  public ArtifactModel getArtifactModel() {
    return myParent.getArtifactModel();
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return myParent.getModulesProvider();
  }

  @NotNull
  public FacetsProvider getFacetsProvider() {
    return myParent.getFacetsProvider();
  }

  public void queueValidation() {
    myEditor.queueValidation();
  }

  @NotNull
  public ArtifactType getArtifactType() {
    return myEditor.getArtifact().getArtifactType();
  }
}
