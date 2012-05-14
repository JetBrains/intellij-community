/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.configuration;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Panel which contains a number of buttons (choices) and a customized subpanel which corresponds to the chosen
 * button.
 */
final class ChoicePanel
  extends JPanel
{
// ------------------------------ FIELDS ------------------------------

  private final ChoiceObject[] choices;
  private       int            choice;
  private final String         choicePanelName;

// --------------------------- CONSTRUCTORS ---------------------------

  ChoicePanel(final ChoiceObject[] choices,
              final String choicePanelName)
  {
    super(new GridBagLayout());
    this.choices = choices;
    choice = 0;
    this.choicePanelName = choicePanelName;
    initializeChoices();
  }

  private void initializeChoices() {
    for (ChoiceObject choice1 : choices) {
      choice1.createObject();
    }
    choice = 0;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public String toString() {
    return choices[choice].toString();
  }

// -------------------------- OTHER METHODS --------------------------

  void configurePanel() {
    final Border border = BorderFactory.createEmptyBorder();
    final TitledBorder tb = new TitledBorder(border, choicePanelName);
    setBorder(tb);
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridheight = 1;
    constraints.weighty = 0;
    final ButtonGroup group = new ButtonGroup();
    final JRadioButton[] buttons = new JRadioButton[choices.length];
    final JPanel[] panels = new JPanel[choices.length];
    final Dimension max = new Dimension();
    // make two columns of choice buttons to save some panel space.
    for (int i = 0; i < choices.length; i++) {
      buttons[i] = new JRadioButton(choices[i].buttonName);
      group.add(buttons[i]);
      constraints.gridx = i % 2;
      constraints.gridy = i >> 1;
      constraints.gridwidth = (i % 2 == 1) ?
                              GridBagConstraints.REMAINDER : 1;
      constraints.weightx = (i % 2 == 1) ? 1 : 0;
      add(buttons[i], constraints);
      choices[i].editingObject = choices[i].choiceObject.deepCopy();
      panels[i] = choices[i].createJPanel();
      /**
       * calculate largest dimension of any of the replaceable panels.
       */
      final Dimension dimension = panels[i].getPreferredSize();
      if (dimension.height > max.height) max.height = dimension.height;
      if (dimension.width > max.width) max.width = dimension.width;
    }
    buttons[choice].setSelected(true);
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.gridwidth = 2;
    constraints.weighty = 1;
    constraints.gridx = 0;
    constraints.gridy = (choices.length >> 1) + 1;
    for (int i = 0; i < choices.length; i++) {
      final int j = i;
      panels[i].setPreferredSize(max);
      buttons[i].addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          if (buttons[j].isSelected()) {
            remove(choices.length);
            add(panels[j], constraints);
            panels[j].invalidate();
            panels[j].repaint();
            validate();
            choice = j;
          }
        }
      });
    }
    final JPanel beginning_panel = panels[choice];
    add(beginning_panel, constraints);
  }

  void copyChoices(final ChoicePanel currentMsg) {
    System.arraycopy(currentMsg.choices, 0, choices, 0, choices.length);
    choice = currentMsg.choice;
  }

  void acceptEdited() {
    for (ChoiceObject choice1 : choices) {
      choice1.acceptEdited();
    }
  }

  ChoiceObject getChoice() {
    return choices[choice];
  }

  public ChoiceObject[] getChoices() {
    return choices;
  }

  public void setChoice(int choice) {
    this.choice = choice;
  }

  public String getChoicePanelName() {
    return choicePanelName;
  }
}
