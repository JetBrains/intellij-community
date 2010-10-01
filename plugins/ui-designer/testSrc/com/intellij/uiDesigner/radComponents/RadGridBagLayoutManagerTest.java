/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  @Override
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

  public void testSnapshotRemainder() {
    JPanel jp = new JPanel();
    jp.setLayout(new GridBagLayout());

    GridBagConstraints rightGbc = new GridBagConstraints();
    rightGbc.gridx      = 1;
    rightGbc.gridy      = 0;
    rightGbc.gridheight = GridBagConstraints.REMAINDER;
    rightGbc.fill       = GridBagConstraints.BOTH;
    rightGbc.weightx    = 1.0;
    rightGbc.weighty    = 1.0;
    rightGbc.anchor     = GridBagConstraints.NORTHWEST;

    JPanel rightPanel = new JPanel();
    jp.add(rightPanel, rightGbc);
    JTextArea jta = new JTextArea("I am filling all the height.\nLine 2.\nLine 3.\nLine 4.");
    rightPanel.add(jta);

    GridBagConstraints leftGbc = new GridBagConstraints();
    leftGbc.gridx  = 0;
    leftGbc.fill   = GridBagConstraints.HORIZONTAL;
    leftGbc.anchor = GridBagConstraints.NORTHWEST;
    leftGbc.ipadx  = 10;
    leftGbc.ipady  = 4;

    JButton jb1 = new JButton("Button 1");
    jp.add(jb1, leftGbc);
    JButton jb2 = new JButton("Button 2");
    jp.add(jb2, leftGbc);
    JButton jb3 = new JButton("Button 3");
    jp.add(jb3, leftGbc);

    processSnapshot(jp);

    assertEquals(4, myContainer.getComponentCount());
    assertEquals(1, myContainer.getComponent(0).getConstraints().getColumn());
    assertEquals(0, myContainer.getComponent(0).getConstraints().getRow());
    assertEquals(3, myContainer.getComponent(0).getConstraints().getRowSpan());
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
