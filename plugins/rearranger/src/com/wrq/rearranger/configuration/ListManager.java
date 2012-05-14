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

import com.wrq.rearranger.util.IconUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Generic manager for a panel with a list combo box, add/delete/copy buttons,
 * a name field, and a subpanel to get contents of current item.
 */
public class ListManager
  extends JPanel
{
  JLabel             nameLabel;
  JTextField         name;
  JButton            addButton;
  JButton            removeButton;
  JButton            copyButton;
  JList              list;
  GridBagConstraints constraints;
  final IListManagerObjectFactory factory;
  IListManagerObject currentObject;
  boolean            buttonPressInProgress;

  private JButton makeButton(String iconName) {
    Icon icon = IconUtil.getIcon(iconName);
    JButton result = new JButton(icon);
    result.setPreferredSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
    return result;
  }

  public ListManager(final IListManagerObjectFactory factory, String nameLabelText) {
    super(new GridBagLayout());
    this.factory = factory;
    currentObject = null;
    nameLabel = new JLabel(nameLabelText);
    name = new JTextField(3);
    addButton = makeButton("general/add");
    removeButton = makeButton("general/remove");
    copyButton = makeButton("general/copy");
    list = new JList(new DefaultListModel());
    /**
     * declare callback handlers for buttons.
     */
    addButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          buttonPressInProgress = true;
          currentObject = factory.create(getUniqueName("unnamed"));
          factory.getObjectList().add(currentObject);
          ((DefaultListModel)list.getModel()).addElement(currentObject);
          list.setSelectedIndex(list.getModel().getSize() - 1);
          updateListSelection();
          buttonPressInProgress = false;
        }
      }
    );
    removeButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          buttonPressInProgress = true;
          DefaultListModel dlm = ((DefaultListModel)list.getModel());
          int selectedIndex = list.getSelectedIndex();
          IListManagerObject lmo = (IListManagerObject)dlm.getElementAt(selectedIndex);
          factory.getObjectList().remove(lmo);
          dlm.remove(selectedIndex);
          if (selectedIndex >= dlm.getSize()) {
            selectedIndex = dlm.getSize() - 1;
          }
          list.setSelectedIndex(selectedIndex);
          currentObject =
            (IListManagerObject)(selectedIndex < 0 ? null : dlm.getElementAt(selectedIndex));
          updateListSelection();
          buttonPressInProgress = false;
        }
      }
    );
    copyButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          buttonPressInProgress = true;
          DefaultListModel dlm = ((DefaultListModel)list.getModel());
          int selectedIndex = list.getSelectedIndex();
          IListManagerObject lmo = (IListManagerObject)dlm.getElementAt(selectedIndex);
          currentObject = lmo.deepcopy();
          currentObject.setDescription(getUniqueName(lmo.getDescription()));
          dlm.addElement(currentObject);
          list.setSelectedIndex(list.getModel().getSize() - 1);
          updateListSelection();
          buttonPressInProgress = false;
        }
      }
    );
    name.getDocument().addDocumentListener(
      new DocumentListener() {
        private void updateList() {
          if (buttonPressInProgress) {
            return;
          }
          String newText = name.getText();
          final IListManagerObject listObject = (IListManagerObject)list.getSelectedValue();
          if (listObject != null) {
            listObject.setDescription(newText);
          }
          int index = list.getSelectedIndex();
          DefaultListModel dlm = ((DefaultListModel)list.getModel());
          dlm.setElementAt(listObject, index);
          validate();
        }

        public void changedUpdate(DocumentEvent e) {
          updateList();
        }

        public void insertUpdate(DocumentEvent e) {
          updateList();
        }

        public void removeUpdate(DocumentEvent e) {
          updateList();
        }
      }
    );
    list.addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (buttonPressInProgress) {
            return;   // list selection changes due to add/remove/copy button handled elsewhere
          }
          buttonPressInProgress = true;
          int selectedIndex = list.getSelectedIndex();
          DefaultListModel dlm = ((DefaultListModel)list.getModel());
          currentObject =
            (IListManagerObject)(selectedIndex < 0 ? null : ((java.lang.Object)dlm.getElementAt(selectedIndex)));
          updateListSelection();
          buttonPressInProgress = false;
        }
      }
    );
  }

  public final JPanel getPane() {
    constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridwidth = 1;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.weightx = 0.0d;
    constraints.weighty = 0.0d;
    constraints.gridy = constraints.gridx = 0;
    constraints.insets = new Insets(0, 0, 0, 5);
    JPanel buttonPanel = new JPanel(new GridBagLayout());
    buttonPanel.setBorder(BorderFactory.createEtchedBorder());  // todo remove
    addButton.setBorderPainted(false);
    removeButton.setBorderPainted(false);
    copyButton.setBorderPainted(false);
    buttonPanel.add(addButton, constraints);
    constraints.gridx++;
    buttonPanel.add(removeButton, constraints);
    constraints.gridx++;
//        constraints.gridwidth = GridBagConstraints.REMAINDER;
//        constraints.weightx = 1;
    buttonPanel.add(copyButton, constraints);

    constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.weightx = 0.0d;
    constraints.weighty = 0.0d;
    constraints.gridy = constraints.gridx = 0;
    constraints.insets = new Insets(3, 3, 3, 3);
    add(buttonPanel, constraints);
    buttonPanel.setPreferredSize(buttonPanel.getPreferredSize()); // todo determine effect if any
    constraints.gridx++;
    add(nameLabel, constraints);
    constraints.gridx++;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    add(name, constraints);


    list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        addButton.setEnabled(true);
    JScrollPane scrollPane = new JScrollPane(list);
    Dimension d = buttonPanel.getPreferredSize();
    d.width = d.width * 5 / 3;
    scrollPane.setPreferredSize(d);
    scrollPane.setMinimumSize(d);
    for (Object object : factory.getObjectList()) {
      ((DefaultListModel)list.getModel()).addElement(object);
    }
    list.setSelectedIndex(list.getModel().getSize() - 1);
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.gridwidth = 1;
    constraints.gridx = 0;
    constraints.gridy++;
    constraints.weightx = 0;
    constraints.weighty = 0;
    constraints.fill = GridBagConstraints.VERTICAL;
    add(scrollPane, constraints);
    constraints.gridx++;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1;
    constraints.weighty = 1;
    final JPanel contents = new JPanel();
    contents.setBorder(BorderFactory.createEtchedBorder()); // todo remove
    add(contents, constraints);
    updateListSelection();
    validate();
    return this;
  }

  /**
   * Enable add/remove/copy buttons and fill in name of selected list item.  Called whenever
   * the list selection changes.
   */
  private void updateListSelection() {
    if (list.getModel().getSize() > 0) {
      name.setEnabled(true);
      nameLabel.setEnabled(true);
      name.setText(((IListManagerObject)list.getSelectedValue()).getDescription());
      removeButton.setEnabled(true);
      copyButton.setEnabled(true);
    }
    else {
      name.setEnabled(false);
      nameLabel.setEnabled(false);
      removeButton.setEnabled(false);
      copyButton.setEnabled(false);
      name.setText("");
    }
    remove(getComponentCount() - 1);
    final JPanel contents = new JPanel();
    contents.setBorder(BorderFactory.createEtchedBorder()); // todo remove
    add(currentObject == null ? contents : ((javax.swing.JPanel)currentObject.getPanel()), constraints);
    validate();
  }

  public String getUniqueName(String name) {
    String result;
    if (name != null) {
      // test initially supplied name; if not duplicate, return it.
      if (!dupName(name)) {
        return name;
      }
    }
    result = (name == null) ? "unnamed" : name;
    int suffix = 0;
    String testName;
    do {
      suffix++;
      testName = result + "_" + suffix;
    }
    while (dupName(testName));
    return testName;
  }

  /**
   * @param name
   * @return true if the supplied name is identical to any other primary method name.
   */
  public boolean dupName(String name) {
    for (int i = 0; i < list.getModel().getSize(); i++) {
      IListManagerObject lmo = (IListManagerObject)list.getModel().getElementAt(i);
      if (lmo.getDescription().equals(name)) {
        return true;
      }
    }
    return false;  //To change body of created methods use File | Settings | File Templates.
  }
}