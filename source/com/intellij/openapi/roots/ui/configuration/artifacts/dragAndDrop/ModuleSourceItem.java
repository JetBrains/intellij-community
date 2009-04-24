package com.intellij.openapi.roots.ui.configuration.artifacts.dragAndDrop;

import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.openapi.module.Module;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ModuleSourceItem extends PackagingSourceItem {
  private final Module myModule;

  public ModuleSourceItem(Module module) {
    myModule = module;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append(myModule.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.setIcon(myModule.getModuleType().getNodeIcon(false));
  }

  @NotNull
  public PackagingElement createElement() {
    return new ModuleOutputPackagingElement(myModule.getName());
  }
}
