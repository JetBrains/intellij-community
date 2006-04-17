/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.fileEditor.impl;

import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * @author max
 */
public class FileEditorInfoPane extends JPanel {
  private int myCounter = 0;
  private final JPanel myCards;
  private final JButton myPrevButton;
  private final JButton myNextButton;
  private java.util.List<JComponent> myComponents;

  public FileEditorInfoPane() {
    super(new BorderLayout());
    final CardLayout layout = new CardLayout();
    myCards = new JPanel(layout);
    myComponents = new ArrayList<JComponent>();
    add(myCards, BorderLayout.CENTER);
    JPanel buttonsPanel = new JPanel(new GridLayout(1, 2));
    myPrevButton = new JButton("<");
    myNextButton = new JButton(">");

    buttonsPanel.add(myPrevButton);
    buttonsPanel.add(myNextButton);

    myPrevButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        layout.previous(myCards);
        updateButtons();
      }
    });

    myNextButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        layout.next(myCards);
        updateButtons();
      }
    });

    myPrevButton.setVisible(false);
    myNextButton.setVisible(false);

    add(buttonsPanel, BorderLayout.EAST);

    setBorder(IdeBorderFactory.createBorder());
  }

  public void addInfo(JComponent component) {
    myComponents.add(component);
    myCards.add(component, String.valueOf(myCounter++));
    updateButtons();
    validate();
  }

  public void removeInfo(JComponent component) {
    myComponents.remove(component);
    myCards.remove(component);
    updateButtons();
    validate();
  }

  private int getCurrentCard() {
    for (int i = 0; i < myComponents.size(); i++) {
      if (myComponents.get(i).isVisible()) return i;
    }
    return -1;
  }

  private void updateButtons() {
    int count = myComponents.size();
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
