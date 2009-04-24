package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.Disposable;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.elements.PackagingElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface ArtifactsEditor extends Disposable {
  DataKey<ArtifactsEditor> ARTIFACTS_EDITOR_KEY = DataKey.create("artifactsEditor");


  void addNewPackagingElement(@Nullable CompositePackagingElementType<?> parentType, @NotNull PackagingElementType<?> type);

  void removeSelectedElements();
}
