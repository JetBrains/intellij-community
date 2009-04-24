package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.PackagingEditorContext;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author nik
 */
public class ArtifactConfigurable extends NamedConfigurable<Artifact> {
  private final Artifact myOriginalArtifact;
  private final PackagingEditorContext myPackagingEditorContext;
  private final ArtifactsEditorImpl myEditor;

  public ArtifactConfigurable(Artifact originalArtifact, PackagingEditorContext packagingEditorContext) {
    super(true, null);
    myOriginalArtifact = originalArtifact;
    myPackagingEditorContext = packagingEditorContext;
    myEditor = new ArtifactsEditorImpl(packagingEditorContext, originalArtifact);
  }

  public void setDisplayName(String name) {
    final String oldName = getArtifact().getName();
    if (name != null && !name.equals(oldName)) {
      myPackagingEditorContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact).setName(name);
    }
  }

  private Artifact getArtifact() {
    return myPackagingEditorContext.getArtifactModel().getModifiableOrOriginal(myOriginalArtifact);
  }

  public Artifact getEditableObject() {
    return getArtifact();
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("banner.slogan.artifact.0", getDisplayName());
  }

  public JComponent createOptionsPanel() {
    return myEditor.createMainComponent();
  }

  @Nls
  public String getDisplayName() {
    return getArtifact().getName();
  }

  public Icon getIcon() {
    return ArtifactsStructureConfigurable.ARTIFACT_ICON;
  }

  public String getHelpTopic() {
    return null;
  }

  public boolean isModified() {
    return myEditor.isModified();
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  public void disposeUIResources() {
    Disposer.dispose(myEditor);
  }
}
