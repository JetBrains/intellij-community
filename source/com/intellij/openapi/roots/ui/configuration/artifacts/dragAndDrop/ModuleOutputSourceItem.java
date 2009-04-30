package com.intellij.openapi.roots.ui.configuration.artifacts.dragAndDrop;

import com.intellij.openapi.module.Module;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Collections;

/**
 * @author nik
 */
public class ModuleOutputSourceItem extends PackagingSourceItem {
  private final Module myModule;

  public ModuleOutputSourceItem(Module module) {
    myModule = module;
  }


  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append("'" + myModule.getName() + "' compile output", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.setIcon(myModule.getModuleType().getNodeIcon(false));
  }

  @NotNull
  public List<? extends PackagingElement<?>> createElements() {
    return Collections.singletonList(new ModuleOutputPackagingElement(myModule.getName()));
  }
}
