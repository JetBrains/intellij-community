package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;

import javax.swing.*;
import java.awt.*;

/**
 * TODO: remove it together witrh DependenciesEditor
 */
public class ModuleChooserElement implements ElementsChooser.ElementProperties{
  private final String myName;
  private final Module myModule;
  private ModuleOrderEntry myOrderEntry;

  public ModuleChooserElement(Module module, ModuleOrderEntry orderEntry) {
    myModule = module;
    myOrderEntry = orderEntry;
    myName = module != null? module.getName() : orderEntry.getModuleName();
  }

  public Module getModule() {
    return myModule;
  }

  public ModuleOrderEntry getOrderEntry() {
    return myOrderEntry;
  }

  public void setOrderEntry(ModuleOrderEntry orderEntry) {
    myOrderEntry = orderEntry;
  }

  public String getName() {
    return myName;
  }

  public Icon getIcon() {
    if (myModule != null) {
      return myModule.getModuleType().getNodeIcon(false);
    }
    else {
      return CellAppearanceUtils.INVALID_ICON;
    }
  }

  public Color getColor() {
    return myModule == null ? Color.RED : null;
  }

  public String toString() {
    return getName();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleChooserElement)) return false;

    final ModuleChooserElement chooserElement = (ModuleChooserElement)o;

    if (!myName.equals(chooserElement.myName)) return false;

    return true;
  }

  public int hashCode() {
    return myName.hashCode();
  }
}
