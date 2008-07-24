package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
abstract class LibraryNodeBase extends PackagingTreeNode {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.packaging.LibraryNodeBase");
  protected final LibraryLink myLibraryLink;

  public LibraryNodeBase(final @Nullable PackagingArtifact owner, @NotNull LibraryLink libraryLink) {
    super(owner);
    myLibraryLink = libraryLink;
  }

  @Override
  public ContainerElement getContainerElement() {
    return myLibraryLink;
  }

  public boolean canNavigate() {
    return true;
  }

  @Override
  public String getTooltipText() {
    if (belongsToIncludedArtifact()) {
      PackagingArtifact owner = getOwner();
      LOG.assertTrue(owner != null);
      return ProjectBundle.message("node.text.packaging.included.from.0", owner.getDisplayName());
    }
    return null;
  }

  @Override
  public void renderTooltip(final ColoredTreeCellRenderer renderer) {
    if (belongsToIncludedArtifact()) {
      PackagingArtifact owner = getOwner();
      LOG.assertTrue(owner != null);
      renderer.append(" " + ProjectBundle.message("node.text.packaging.included.from.0", owner.getDisplayName()), getCommentAttributes());
    }
  }

  public void navigate(final ModuleStructureConfigurable configurable) {
    Module parentModule = myLibraryLink.getParentModule();
    if (parentModule == null) {
      return;
    }

    final PackagingArtifact owner = getOwner();
    if (owner != null) {
      owner.navigate(configurable, myLibraryLink);
    }
    else {
      ModulesConfigurator modulesConfigurator = configurable.getContext().getModulesConfigurator();
      ModuleRootModel rootModel = modulesConfigurator.getRootModel(parentModule);
      LibraryOrderEntry orderEntry = OrderEntryUtil.findLibraryOrderEntry(rootModel, myLibraryLink.getLibrary(), true, modulesConfigurator);
      configurable.selectOrderEntry(orderEntry != null ? orderEntry.getOwnerModule() : parentModule, orderEntry);
    }
  }

  public Object getSourceObject() {
    return myLibraryLink.getLibrary();
  }

  public LibraryLink getLibraryLink() {
    return myLibraryLink;
  }

}
