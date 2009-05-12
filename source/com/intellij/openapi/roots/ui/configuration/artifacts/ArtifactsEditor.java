package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.PackagingElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface ArtifactsEditor extends Disposable {
  DataKey<ArtifactsEditor> ARTIFACTS_EDITOR_KEY = DataKey.create("artifactsEditor");


  void addNewPackagingElement(@NotNull PackagingElementType<?> type);

  void removeSelectedElements();

  LayoutTreeComponent getPackagingElementsTree();

  Artifact getArtifact();

  ArtifactRootElement<?> getRootElement();
}
