package com.intellij.ui.plaf.beg;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalTreeUI;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;

public class BegTreeUI extends MetalTreeUI {
  protected static final String PROP_LINE_STYLE = "JTree.lineStyle";
  protected static final int DASHED = 0;
  protected static final int SOLID = 1;
  protected static final int NONE = 2;
  protected int myLineStyle;
  protected PropertyChangeListener myPropertyChangeListener;

  /* Invoked by reflection */
  public static ComponentUI createUI(JComponent c) {
    return new BegTreeUI();
  }

  protected boolean isToggleSelectionEvent(MouseEvent e) {
    return SwingUtilities.isLeftMouseButton(e) && e.isControlDown() && !e.isPopupTrigger();
  }
}