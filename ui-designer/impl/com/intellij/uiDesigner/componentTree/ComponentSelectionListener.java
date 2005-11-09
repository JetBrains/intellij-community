package com.intellij.uiDesigner.componentTree;

import com.intellij.uiDesigner.designSurface.GuiEditor;

import java.util.EventListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface ComponentSelectionListener extends EventListener{
  void selectedComponentChanged(GuiEditor source);
}
