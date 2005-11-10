/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.structuralsearch.inspection;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceDialog;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.SearchDialog;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.List;

/**
 * @author cdr
 */
public class SSBasedInspectionOptions {

  private JPanel myPanel;
  private JList myTemplatesList;
  private JButton myAddSearchButton;
  private JButton myRemoveButton;
  private JButton myAddReplaceButton;
  private JButton myEditButton;

  // for externalization
  private final List<Configuration> myConfigurations;

  public SSBasedInspectionOptions(final List<Configuration> configurations) {
    myConfigurations = configurations;
    myTemplatesList.setModel(new MyListModel());
    myTemplatesList.setCellRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel component = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        Configuration configuration = myConfigurations.get(index);
        component.setText(configuration.getName());
        return component;
      }
    });
    myTemplatesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        templateListChanged();
      }
    });
    myTemplatesList.getModel().addListDataListener(new ListDataListener() {
      public void intervalAdded(ListDataEvent e) {
        templateListChanged();
      }

      public void intervalRemoved(ListDataEvent e) {
        templateListChanged();
      }

      public void contentsChanged(ListDataEvent e) {
        templateListChanged();
      }
    });

    myAddSearchButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        addTemplate(new SearchDialogFactory() {
          public SearchDialog createDialog(SearchContext searchContext) {
            return new SearchDialog(searchContext, false, false);
          }
        });
      }
    });
    myAddReplaceButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        addTemplate(new SearchDialogFactory() {
          public SearchDialog createDialog(SearchContext searchContext) {
            return new ReplaceDialog(searchContext, false, false);
          }
        });
      }
    });
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Configuration configuration = (Configuration)myTemplatesList.getSelectedValue();
        if (configuration == null) return;

        SearchDialog dialog = createDialog(new SearchDialogFactory() {
          public SearchDialog createDialog(SearchContext searchContext) {
            return configuration instanceof SearchConfiguration ? new SearchDialog(searchContext, false, false) {
              public Configuration createConfiguration() {
                return configuration;
              }
            } : new ReplaceDialog(searchContext, false, false) {
              public Configuration createConfiguration() {
                return configuration;
              }
            };
          }
        });
        dialog.setValuesFromConfig(configuration);
        dialog.setUseLastConfiguration(true);
        dialog.show();
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Object[] selected = myTemplatesList.getSelectedValues();
        for (Object o : selected) {
          Configuration configuration = ((Configuration)o);
          Iterator<Configuration> iterator = myConfigurations.iterator();
          while (iterator.hasNext()) {
            Configuration configuration1 = iterator.next();
            if (configuration1.getName().equals(configuration.getName())) {
              iterator.remove();
            }
          }
        }
        configurationsChanged();
      }
    });
    // later because InspectionToolPanel enables all controls recursively
    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        templateListChanged();
      }
    });
  }

  private void templateListChanged() {
    boolean somethingSelected = myTemplatesList.getSelectedValue() != null;
    myRemoveButton.setEnabled(somethingSelected);
    myEditButton.setEnabled(somethingSelected);
  }

  interface SearchDialogFactory {
    SearchDialog createDialog(SearchContext searchContext);
  }
  private void addTemplate(SearchDialogFactory searchDialogFactory) {
    SearchDialog dialog = createDialog(searchDialogFactory);
    dialog.show();
    if (!dialog.isOK()) return;
    Configuration configuration = dialog.getConfiguration();

    if (configuration.getName() == null || configuration.getName().equals(SearchDialog.USER_DEFINED)) {
      String name = dialog.showSaveTemplateAsDialog();
      if (name == null) return;
      configuration.setName(name);
    }
    myConfigurations.add(configuration);

    configurationsChanged();
  }

  private static SearchDialog createDialog(final SearchDialogFactory searchDialogFactory) {
    AnActionEvent event = new AnActionEvent(null, DataManager.getInstance().getDataContext(),
                                            "", new DefaultActionGroup().getTemplatePresentation(), ActionManager.getInstance(), 0);
    SearchContext searchContext = new SearchContext();
    searchContext.configureFromDataContext(event.getDataContext());
    SearchDialog dialog = searchDialogFactory.createDialog(searchContext);
    return dialog;
  }

  public void configurationsChanged() {
    ((MyListModel)myTemplatesList.getModel()).fireContentsChanged();
  }

  public JPanel getComponent() {
    return myPanel;
  }

  private class MyListModel extends AbstractListModel {
    public int getSize() {
      return myConfigurations.size();
    }

    public Object getElementAt(int index) {
      return index < myConfigurations.size() ? myConfigurations.get(index) : null;
    }

    public void fireContentsChanged() {
      fireContentsChanged(myTemplatesList, -1, -1);
    }
  }
}
