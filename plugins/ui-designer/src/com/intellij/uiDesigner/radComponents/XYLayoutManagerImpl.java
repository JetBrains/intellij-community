package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.shared.XYLayoutManager;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class XYLayoutManagerImpl extends XYLayoutManager {
  private final Dimension myPreferredSize;
  private static final Dimension MIN_SIZE = new Dimension(20,20);

  public XYLayoutManagerImpl(){
    myPreferredSize = new Dimension();
  }

  public void setPreferredSize(final Dimension size) {
    myPreferredSize.setSize(size);
  }

  public Dimension maximumLayoutSize(final Container container){
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  public Dimension preferredLayoutSize(final Container container){
    return myPreferredSize;
  }

  public void layoutContainer(final Container parent){
  }
  
  public Dimension minimumLayoutSize(final Container container){
    final Container parent = container.getParent();
    if (!(parent instanceof JComponent)) {
      return MIN_SIZE;
    }
    final RadComponent component = (RadComponent)((JComponent)parent).getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT);
    if (component == null) {
      return MIN_SIZE;
    }

    // the following code prevents XYs placed in Grid from being shrunk
    final RadComponent radParent = component.getParent();
    if (radParent instanceof RadContainer && (((RadContainer)radParent).getLayoutManager().isGrid())) {
      return new Dimension(
        Math.max(myPreferredSize.width, MIN_SIZE.width),
        Math.max(myPreferredSize.height, MIN_SIZE.height)
      );
    }
    return MIN_SIZE;
  }
}
