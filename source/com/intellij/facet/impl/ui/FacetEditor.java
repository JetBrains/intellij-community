/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.module.Module;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.ui.UserActivityListener;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.FacetConfiguration;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FacetEditor extends UnnamedConfigurableGroup implements UnnamedConfigurable {
  private FacetEditorTab[] myEditorTabs;
  private JLabel myWarningLabel;
  private final FacetValidatorsManagerImpl myValidatorsManager;

  public FacetEditor(final FacetEditorContext context, final FacetConfiguration configuration) {
    myValidatorsManager = new FacetValidatorsManagerImpl();
    myWarningLabel = new JLabel();
    myWarningLabel.setVisible(false);
    myEditorTabs = configuration.createEditorTabs(context, myValidatorsManager);
    for (Configurable configurable : myEditorTabs) {
      add(configurable);
    }
  }



  public void reset() {
    super.reset();
    myValidatorsManager.validate();
  }

  public JComponent createComponent() {
    final JComponent editorComponent;
    if (myEditorTabs.length > 1) {
      final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper();
      for (FacetEditorTab editorTab : myEditorTabs) {
        tabbedPane.addTab(editorTab.getDisplayName(), editorTab.getIcon(), editorTab.createComponent(), null);
      }
      tabbedPane.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          final int index = tabbedPane.getSelectedIndex();
          myEditorTabs[index].onTabLeaving();
        }
      });
      editorComponent = tabbedPane.getComponent();
    }
    else if (myEditorTabs.length == 1) {
      editorComponent = myEditorTabs[0].createComponent();
    }
    else {
      editorComponent = new JPanel();
    }
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(BorderLayout.CENTER, editorComponent);
    myWarningLabel.setIcon(Messages.getWarningIcon());
    panel.add(BorderLayout.SOUTH, myWarningLabel);

    return panel;
  }

  public void disposeUIResources() {
    myWarningLabel = null;
    super.disposeUIResources();
  }

  public void onFacetAdded(@NotNull Module module) {
    for (FacetEditorTab editorTab : myEditorTabs) {
      editorTab.onFacetAdded(module);
    }
  }

  private class FacetValidatorsManagerImpl implements FacetValidatorsManager {
    private List<FacetEditorValidator> myValidators = new ArrayList<FacetEditorValidator>();

    public void registerValidator(final FacetEditorValidator validator, JComponent... componentsToWatch) {
      myValidators.add(validator);
      final UserActivityWatcher watcher = new UserActivityWatcher();
      for (JComponent component : componentsToWatch) {
        watcher.register(component);
      }
      watcher.addUserActivityListener(new UserActivityListener() {
        public void stateChanged() {
          //todo[nik] run only one validator
          validate();
        }
      });
    }

    public void validate() {
      try {
        for (FacetEditorValidator validator : myValidators) {
          validator.check();
        }
        myWarningLabel.setVisible(false);
      }
      catch (ConfigurationException e) {
        myWarningLabel.setText(e.getMessage());
        myWarningLabel.setVisible(true);
      }
    }
  }
}
