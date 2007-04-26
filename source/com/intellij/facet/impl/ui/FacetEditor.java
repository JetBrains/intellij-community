/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.UnnamedConfigurableGroup;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class FacetEditor extends UnnamedConfigurableGroup implements UnnamedConfigurable {
  private FacetEditorTab[] myEditorTabs;
  private JLabel myWarningLabel;
  private final FacetValidatorsManagerImpl myValidatorsManager;
  private JComponent myComponent;
  private @Nullable TabbedPaneWrapper myTabbedPane;
  private final FacetEditorContext myContext;

  public FacetEditor(final FacetEditorContext context, final FacetConfiguration configuration) {
    myContext = context;
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

  public JComponent getComponent() {
    if (myComponent == null) {
      myComponent = createComponent();
    }
    return myComponent;
  }

  public JComponent createComponent() {
    final JComponent editorComponent;
    if (myEditorTabs.length > 1) {
      myTabbedPane = new TabbedPaneWrapper();
      for (FacetEditorTab editorTab : myEditorTabs) {
        myTabbedPane.addTab(editorTab.getDisplayName(), editorTab.getIcon(), editorTab.createComponent(), null);
      }
      myTabbedPane.addChangeListener(new ChangeListener() {
        private int myTabIndex;

        public void stateChanged(ChangeEvent e) {
          myEditorTabs[myTabIndex].onTabLeaving();
          myTabIndex = myTabbedPane.getSelectedIndex();
          onTabSelected(myTabbedPane, myEditorTabs[myTabIndex]);
        }
      });
      editorComponent = myTabbedPane.getComponent();
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

  private static void onTabSelected(final TabbedPaneWrapper tabbedPane, final FacetEditorTab selectedTab) {
    selectedTab.onTabEntering();
    final JComponent preferredFocusedComponent = selectedTab.getPreferredFocusedComponent();
    if (preferredFocusedComponent != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (tabbedPane.getComponent().isShowing()) {
            preferredFocusedComponent.requestFocus();
          }
        }
      });
    }
  }

  public void disposeUIResources() {
    myWarningLabel = null;
    super.disposeUIResources();
  }

  public void onFacetAdded(@NotNull Facet facet) {
    for (FacetEditorTab editorTab : myEditorTabs) {
      editorTab.onFacetInitialized(facet);
    }
  }

  public void setSelectedTabName(final String tabName) {
    getComponent();
    final TabbedPaneWrapper tabbedPane = myTabbedPane;
    if (tabbedPane == null) return;
    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
      if (tabName.equals(tabbedPane.getTitleAt(i))) {
        tabbedPane.setSelectedIndex(i);
        return;
      }
    }
  }

  public FacetEditorContext getContext() {
    return myContext;
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
