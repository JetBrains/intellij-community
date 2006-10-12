/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 12.10.2006
 * Time: 12:46:47
 */
package com.intellij.uiDesigner.radComponents;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public class RadGridBagLayoutManagerTest extends TestCase {
  private RadGridBagLayoutManager myManager;
  private RadContainer myContainer;

  protected void setUp() throws Exception {
    super.setUp();
    myManager = new RadGridBagLayoutManager();
    myContainer = new RadContainer(null, "grid");
    myContainer.setLayoutManager(myManager);
  }

  public void testSnapshotRelative() {
    GridBagCase gbc = new GridBagCase();
    gbc.addButton("Button 1", GridBagConstraints.RELATIVE, GridBagConstraints.RELATIVE);
    gbc.addButton("Button 2", GridBagConstraints.RELATIVE, GridBagConstraints.RELATIVE);
    gbc.addButton("Button 3", GridBagConstraints.RELATIVE, GridBagConstraints.RELATIVE);

    processSnapshot(gbc);

    assertEquals(3, myContainer.getComponentCount());
    assertEquals(0, myContainer.getComponent(0).getConstraints().getColumn());
    assertEquals(1, myContainer.getComponent(1).getConstraints().getColumn());
    assertEquals(2, myContainer.getComponent(2).getConstraints().getColumn());
  }

  private void processSnapshot(final JPanel panel) {
    for(int i=0; i<panel.getComponentCount(); i++) {
      RadComponent button = new RadAtomicComponent(null, JButton.class, Integer.toString(i));
      myManager.addSnapshotComponent(panel, (JComponent) panel.getComponent(i), myContainer, button);
    }
  }

  private class GridBagCase extends JPanel {
    public GridBagCase() {
      GridBagLayout layout = new GridBagLayout();
      setLayout(layout);
    }

    public Component addButton(final String title, int gridx, int gridy) {
      GridBagConstraints buttonConstraints = new GridBagConstraints();
      buttonConstraints.gridx = gridx;
      buttonConstraints.gridy = gridy;
      buttonConstraints.fill = GridBagConstraints.HORIZONTAL;
      buttonConstraints.anchor = GridBagConstraints.NORTHWEST;
      buttonConstraints.ipadx = 20;
      buttonConstraints.ipady = 20;

      JButton button = new JButton(title);

      add(button, buttonConstraints);
      return button;
    }
  }
}
