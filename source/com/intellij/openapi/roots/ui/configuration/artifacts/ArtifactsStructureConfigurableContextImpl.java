package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactModelImpl;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.packaging.ui.PackagingEditorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
* @author nik
*/
class ArtifactsStructureConfigurableContextImpl implements PackagingEditorContext {
  private ModifiableArtifactModel myModifiableModel;
  private final ManifestFilesInfo myManifestFilesInfo = new ManifestFilesInfo();
  private ArtifactAdapter myModifiableModelListener;
  private StructureConfigurableContext myContext;
  private Project myProject;
  private Map<Artifact, CompositePackagingElement<?>> myModifiableRoots = new HashMap<Artifact, CompositePackagingElement<?>>();
  private Map<Artifact, ArtifactEditorImpl> myArtifactEditors = new HashMap<Artifact, ArtifactEditorImpl>();

  public ArtifactsStructureConfigurableContextImpl(StructureConfigurableContext context, Project project, final ArtifactAdapter modifiableModelListener) {
    myModifiableModelListener = modifiableModelListener;
    myContext = context;
    myProject = project;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public ArtifactModel getArtifactModel() {
    if (myModifiableModel != null) {
      return myModifiableModel;
    }
    return ArtifactManager.getInstance(myProject);
  }

  public CompositePackagingElement<?> getRootElement(@NotNull Artifact originalArtifact) {
    if (myModifiableModel != null) {
      final CompositePackagingElement<?> rootElement = myModifiableModel.getArtifactByOriginal(originalArtifact).getRootElement();
      if (rootElement != originalArtifact.getRootElement()) {
        myModifiableRoots.put(originalArtifact, rootElement);
      }
    }
    CompositePackagingElement<?> root = myModifiableRoots.get(originalArtifact);
    if (root == null) {
      root = ArtifactUtil.copyFromRoot(originalArtifact.getRootElement(), myProject);
      myModifiableRoots.put(originalArtifact, root);
    }
    return root;
  }

  public void ensureRootIsWritable(@NotNull Artifact originalArtifact) {
    final ModifiableArtifact artifact = getModifiableArtifactModel().getOrCreateModifiableArtifact(originalArtifact);
    if (artifact.getRootElement() == originalArtifact.getRootElement()) {
      artifact.setRootElement(getRootElement(originalArtifact));
    }

  }

  public ArtifactEditorImpl getOrCreateEditor(Artifact artifact) {
    ArtifactEditorImpl artifactEditor = myArtifactEditors.get(artifact);
    if (artifactEditor == null) {
      artifactEditor = new ArtifactEditorImpl(this, artifact);
      myArtifactEditors.put(artifact, artifactEditor);
    }
    return artifactEditor;
  }

  @Nullable
  public ModifiableArtifactModel getActualModifiableModel() {
    return myModifiableModel;
  }

  @NotNull
  public ModifiableArtifactModel getModifiableArtifactModel() {
    if (myModifiableModel == null) {
      myModifiableModel = ArtifactManager.getInstance(myProject).createModifiableModel();
      ((ArtifactModelImpl)myModifiableModel).addListener(myModifiableModelListener);
    }
    return myModifiableModel;
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return myContext.getModulesConfigurator();
  }

  @NotNull
  public FacetsProvider getFacetsProvider() {
    return myContext.getModulesConfigurator().getFacetsConfigurator();
  }

  @NotNull
  public ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType) {
    return myManifestFilesInfo.getManifestFile(element, artifactType, this);
  }

  public ManifestFilesInfo getManifestFilesInfo() {
    return myManifestFilesInfo;
  }

  public void resetModifiableModel() {
    disposeUIResources();
    myModifiableModel = null;
    myModifiableRoots.clear();
    myManifestFilesInfo.clear();
  }

  public void disposeUIResources() {
    for (ArtifactEditorImpl editor : myArtifactEditors.values()) {
      Disposer.dispose(editor);
    }
    myArtifactEditors.clear();
  }
}
