package com.intellij.uiDesigner.componentTree;

import com.intellij.uiDesigner.GuiEditor;

import java.util.EventListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface ComponentSelectionListener extends EventListener{
  public void selectedComponentChanged(GuiEditor source);
}
