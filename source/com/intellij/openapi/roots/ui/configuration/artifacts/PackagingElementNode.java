package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;

/**
 * @author nik
 */
public class PackagingElementNode extends ArtifactsTreeNode {
  private final PackagingElement<?> myPackagingElement;
  private final PackagingElementPresentation myPresentation;

  public PackagingElementNode(PackagingElement<?> packagingElement, PackagingEditorContext context) {
    myPackagingElement = packagingElement;
    myPresentation = myPackagingElement.createPresentation(context);
  }

  public PackagingElement<?> getPackagingElement() {
    return myPackagingElement;
  }

  public PackagingElementPresentation getPresentation() {
    return myPresentation;
  }

  public void navigate(ModuleStructureConfigurable structureConfigurable) {
    if (myPackagingElement instanceof ModuleOutputPackagingElement) {
      structureConfigurable.selectModuleTab(((ModuleOutputPackagingElement)myPackagingElement).getModuleName(), null);
    }
  }

  public boolean canNavigate() {
    return false;
  }

  public Object getSourceObject() {
    return null;
  }

  @Override
  public String getName() {
    return myPresentation.getPresentableName();
  }
}
