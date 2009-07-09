package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.PackagingEditorContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public interface ArtifactEditor extends Disposable {
  DataKey<ArtifactEditor> ARTIFACTS_EDITOR_KEY = DataKey.create("artifactsEditor");


  void addNewPackagingElement(@NotNull PackagingElementType<?> type);

  void removeSelectedElements();

  LayoutTreeComponent getLayoutTreeComponent();

  Artifact getArtifact();

  CompositePackagingElement<?> getRootElement();

  PackagingEditorContext getContext();

  JComponent getMainComponent();

  ComplexElementSubstitutionParameters getSubstitutionParameters();
}
