package com.intellij.openapi.roots.ui.configuration.artifacts.dragAndDrop;

import com.intellij.openapi.module.Module;
import com.intellij.packaging.ui.PackagingSourceItemsGroup;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ModuleSourceItemGroup extends PackagingSourceItemsGroup {
  private final Module myModule;

  public ModuleSourceItemGroup(Module module) {
    myModule = module;
  }

  public Module getModule() {
    return myModule;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append(myModule.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.setIcon(myModule.getModuleType().getNodeIcon(false));
  }
}
