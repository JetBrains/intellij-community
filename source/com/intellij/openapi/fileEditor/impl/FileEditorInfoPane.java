/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.fileEditor.impl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author max
 */
public class FileEditorInfoPane extends JPanel {
  private int myCounter = 0;
  private final JPanel myCards;
  private final JButton myPrevButton;
  private final JButton myNextButton;

  public FileEditorInfoPane() {
    super(new BorderLayout());
    final CardLayout layout = new CardLayout();
    myCards = new JPanel(layout);
    add(myCards, BorderLayout.CENTER);
    JPanel buttonsPanel = new JPanel(new GridLayout(1, 2));
    myPrevButton = new JButton("<");
    myNextButton = new JButton(">");

    buttonsPanel.add(myPrevButton);
    buttonsPanel.add(myNextButton);

    myPrevButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        layout.previous(myCards);
      }
    });

    myNextButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        layout.next(myCards);
      }
    });
  }

  public void addInfo(JComponent component) {
    myCards.add(component, String.valueOf(myCounter++));
    updateButtons();
    validate();
  }

  public void removeInfo(JComponent component) {
    myCards.remove(component);
    updateButtons();
    validate();
  }

  private int getCurrentCard() {
    final Component[] components = getComponents();
    for (int i = 0; i < components.length; i++) {
      if (components[i].isVisible()) return i;
    }
    return -1;
  }

  private void updateButtons() {
    int count = getComponentCount();
    if (count > 0) {
      setVisible(true);
      if (count == 1) {
        myNextButton.setVisible(false);
        myPrevButton.setVisible(false);
      }
      else {
        myNextButton.setVisible(true);
        myPrevButton.setVisible(true);
        int currentCard = getCurrentCard();
        myNextButton.setEnabled(currentCard + 1 < count);
        myPrevButton.setEnabled(currentCard - 1 >= 0);
      }
    }
    else {
      setVisible(false);
    }
  }
}
