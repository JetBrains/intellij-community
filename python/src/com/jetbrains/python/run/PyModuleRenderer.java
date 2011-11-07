package com.jetbrains.python.run;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

/**
* @author yole
*/
public class PyModuleRenderer extends HtmlListCellRenderer<Module> {
  public PyModuleRenderer(final ListCellRenderer renderer) {
    super(renderer);
  }

  @Override
  protected void doCustomize(JList list, Module module, int index, boolean selected, boolean hasFocus) {
    if (module == null) {
      append("[none]", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else {
      setIcon(ModuleType.get(module).getNodeIcon(false));
      append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
