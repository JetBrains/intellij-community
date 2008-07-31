package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
abstract class ModuleOutputBaseNode extends PackagingTreeNode {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.packaging.ModuleOutputBaseNode");
  protected final ModuleLink myModuleLink;

  public ModuleOutputBaseNode(final @Nullable PackagingArtifact owner, final ModuleLink moduleLink) {
    super(owner);
    myModuleLink = moduleLink;
  }

  public ModuleLink getModuleLink() {
    return myModuleLink;
  }

  public boolean canNavigate() {
    return true;
  }

  public void navigate(final ModuleStructureConfigurable configurable) {
    Module parentModule = myModuleLink.getParentModule();
    if (parentModule == null) return;

    PackagingArtifact owner = getOwner();
    if (owner != null) {
      owner.navigate(configurable, myModuleLink);
    }
    else {
      ModulesConfigurator modulesConfigurator = configurable.getContext().getModulesConfigurator();
      ModuleRootModel rootModel = modulesConfigurator.getRootModel(parentModule);
      ModuleOrderEntry orderEntry = OrderEntryUtil.findModuleOrderEntry(rootModel, myModuleLink.getModule(), true, modulesConfigurator);
      configurable.selectOrderEntry(orderEntry != null ? orderEntry.getOwnerModule() : parentModule, orderEntry);
    }
  }

  public double getWeight() {
    return PackagingNodeWeights.MODULE;
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

  public Object getSourceObject() {
    return myModuleLink.getModule();
  }

  public ContainerElement getContainerElement() {
    return myModuleLink;
  }
}
