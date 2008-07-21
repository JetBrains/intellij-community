package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class PackagingArtifact {

  @NotNull
  public abstract String getOutputFileName();

  public abstract void render(@NotNull ColoredTreeCellRenderer renderer, final SimpleTextAttributes mainAttributes,
                              final SimpleTextAttributes commentAttributes);

  public abstract void navigate(ModuleStructureConfigurable configurable);

  public abstract String getDisplayName();

  @Nullable
  public ContainerElement getContainerElement() {
    return null;
  }
}
