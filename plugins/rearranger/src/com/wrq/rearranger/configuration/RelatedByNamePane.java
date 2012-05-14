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

import com.wrq.rearranger.settings.PrimaryMethodSetting;
import com.wrq.rearranger.settings.RelatedByNameMethodsSettings;

import javax.swing.*;
import java.util.List;

/** Configuration settings for handling ordering of extracted methods. */
public class RelatedByNamePane {
  RelatedByNameMethodsSettings rbnms;

  public RelatedByNamePane(RelatedByNameMethodsSettings rbnms) {
    this.rbnms = rbnms;
  }

  public final JPanel getPane() {
    IListManagerObjectFactory lmof = new IListManagerObjectFactory() {
      public IListManagerObject create(String name) {
        PrimaryMethodSetting pms = new PrimaryMethodSetting();
        pms.setDescription(name);
        return pms;
      }

      public List getObjectList() {
        return rbnms.getMethodList();
      }
    };
    ListManager result = new ListManager(lmof, "Primary method name:");
//        constraints = new GridBagConstraints();
//        constraints.anchor = GridBagConstraints.NORTHWEST;
//        constraints.fill = GridBagConstraints.NONE;
//        constraints.gridwidth = 1;
//        constraints.gridheight = 1;
//        constraints.weightx = 0.0d;
//        constraints.weighty = 0.0d;
//        constraints.gridy = constraints.gridx = 0;
//        constraints.insets = new Insets(3, 3, 3, 3);
//        addButton.setBorderPainted(false);
//        removeButton.setBorderPainted(false);
//        copyButton.setBorderPainted(false);
//        containerPanel.add(addButton, constraints);
//        constraints.gridx++;
//        containerPanel.add(removeButton, constraints);
//        constraints.gridx++;
//        containerPanel.add(copyButton, constraints);
//        constraints.gridx++;
//        containerPanel.add(nameLabel, constraints);
//        constraints.gridx++;
//        constraints.gridwidth = GridBagConstraints.REMAINDER;
//        constraints.weightx = 1;
//        constraints.fill = GridBagConstraints.HORIZONTAL;
//        containerPanel.add(name, constraints);
//        /**
//         * declare callback handlers for buttons.
//         */
//        addButton.addActionListener(new ActionListener()
//        {
//            public void actionPerformed(ActionEvent e)
//            {
//                PrimaryMethodSetting pms = new PrimaryMethodSetting();
//                pms.setName("unnamed");
//                ((DefaultListModel)rbnmlist.getModel()).addElement(pms);
//                rbnmlist.setSelectedIndex(rbnmlist.getModel().getSize() - 1);
//                updateListSelection();
//            }
//        });
//        removeButton.addActionListener(new ActionListener()
//        {
//            public void actionPerformed(ActionEvent e)
//            {
//                DefaultListModel dlm = ((DefaultListModel)rbnmlist.getModel());
//                int selectedIndex = rbnmlist.getSelectedIndex();
//                PrimaryMethodSetting pms = (PrimaryMethodSetting) dlm.getElementAt(selectedIndex);
//                rbnms.getMethodList().remove(pms);
//                dlm.remove(selectedIndex);
//                if (selectedIndex >= dlm.getSize()) {
//                    selectedIndex = dlm.getSize() - 1;
//                }
//                rbnmlist.setSelectedIndex(selectedIndex);
//                updateListSelection();
//            }
//        });
//        copyButton.addActionListener(new ActionListener()
//        {
//            public void actionPerformed(ActionEvent e)
//            {
//                DefaultListModel dlm = ((DefaultListModel)rbnmlist.getModel());
//                int selectedIndex = rbnmlist.getSelectedIndex();
//                PrimaryMethodSetting pms = (PrimaryMethodSetting) dlm.getElementAt(selectedIndex);
//                dlm.addElement(pms.deepcopy());
//                rbnmlist.setSelectedIndex(rbnmlist.getModel().getSize() - 1);
//                updateListSelection();
//            }
//        });
//        rbnmlist.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        addButton.setEnabled(true);
//        name.getDocument().addDocumentListener(new DocumentListener()
//        {
//            private void updateList()
//            {
//                String newText = name.getText();
//                final PrimaryMethodSetting primaryMethodSetting = (PrimaryMethodSetting)rbnmlist.getSelectedValue();
//                primaryMethodSetting.setName(newText);
//                int index = rbnmlist.getSelectedIndex();
//                DefaultListModel dlm = ((DefaultListModel)rbnmlist.getModel());
//                dlm.remove(index);
//                dlm.add(index, primaryMethodSetting);
//                rbnmlist.setSelectedIndex(index);
//            }
//            public void changedUpdate(DocumentEvent e)
//            {
//                updateList();
//            }
//
//            public void insertUpdate(DocumentEvent e)
//            {
//                updateList();
//            }
//
//            public void removeUpdate(DocumentEvent e)
//            {
//                updateList();
//            }
//        });
//        JScrollPane scrollPane = new JScrollPane(rbnmlist);
//        scrollPane.setPreferredSize(new Dimension(80, 300));
//        ListIterator li = rbnms.getMethodList().listIterator();
//        while (li.hasNext())
//        {
//            PrimaryMethodSetting pms = (PrimaryMethodSetting) li.next();
//            ((DefaultListModel)rbnmlist.getModel()).addElement(pms);
//        }
//        rbnmlist.setSelectedIndex(rbnmlist.getModel().getSize() - 1);
//        constraints.gridheight = GridBagConstraints.REMAINDER;
//        constraints.gridx = 0;
//        constraints.gridy++;
//        constraints.gridwidth = 3;
//        constraints.weightx = .2;
//        constraints.weighty = 1;
//        constraints.fill = GridBagConstraints.BOTH;
//        containerPanel.add(scrollPane, constraints);
//        constraints.gridx += 3;
//        constraints.gridwidth = GridBagConstraints.REMAINDER;
//        constraints.fill = GridBagConstraints.BOTH;
//        constraints.weightx = .8;
//        containerPanel.add(getPrimaryMethodPanel(), constraints);
//        updateListSelection();
    return result.getPane();
  }

  /**
   * Enable add/remove/copy buttons and fill in name of selected list item.  Called whenever
   * the list selection changes.
   */
//    private void updateListSelection()
//    {
//        if (rbnmlist.getModel().getSize() > 0)
//        {
//            rbnmlist.setSelectedIndex(0);
//            name.setEnabled(true);
//            nameLabel.setEnabled(true);
//            name.setText(((PrimaryMethodSetting) rbnmlist.getSelectedValue()).getName());
//            removeButton.setEnabled(true);
//            copyButton.setEnabled(true);
//        }
//        else
//        {
//            name.setEnabled(false);
//            nameLabel.setEnabled(false);
//            removeButton.setEnabled(false);
//            copyButton.setEnabled(false);
//            name.setText("");
//        }
//        containerPanel.remove(containerPanel.getComponentCount() - 1);
//        containerPanel.add(getPrimaryMethodPanel(), constraints);
//        containerPanel.validate();
//    }

  /**
   * Get a primary method panel based on values for the currently selected primary item.
   * @return
   */
//    private JPanel getPrimaryMethodPanel()
//    {
//        PrimaryMethodSetting pms = (PrimaryMethodSetting) rbnmlist.getSelectedValue();
//        if (pms == null) {
//            return new JPanel(); // empty
//        }
//        else {
//            return pms.getPanel();
//        }
//    }

// ---------- START Level 4 ----------
}
