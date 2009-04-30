package com.intellij.openapi.roots.ui.configuration.artifacts.dragAndDrop;

import com.intellij.packaging.ui.PackagingSourceItemsGroup;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ModuleGroupItem extends PackagingSourceItemsGroup {
  private final String myGroupName;
  private final String[] myPath;

  public ModuleGroupItem(String[] path) {
    myGroupName = path[path.length - 1];
    myPath = path;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.setIcon(Icons.CLOSED_MODULE_GROUP_ICON);
    renderer.append(myGroupName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public String[] getPath() {
    return myPath;
  }
}
