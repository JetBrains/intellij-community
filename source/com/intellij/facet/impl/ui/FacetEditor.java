/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.UnnamedConfigurableGroup;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class FacetEditor extends UnnamedConfigurableGroup implements UnnamedConfigurable {
  private FacetEditorTab[] myEditorTabs;
  private FacetErrorPanel myErrorPanel;
  private JComponent myComponent;
  private @Nullable TabbedPaneWrapper myTabbedPane;
  private final FacetEditorContext myContext;
  private final Set<FacetEditorTab> myVisitedTabs = new HashSet<FacetEditorTab>();
  private int mySelectedTabIndex = 0;

  public FacetEditor(final FacetEditorContext context, final FacetConfiguration configuration) {
    myContext = context;
    myErrorPanel = new FacetErrorPanel();
    myEditorTabs = configuration.createEditorTabs(context, myErrorPanel.getValidatorsManager());
    for (Configurable configurable : myEditorTabs) {
      add(configurable);
    }
  }

  public void reset() {
    super.reset();
    myErrorPanel.getValidatorsManager().validate();
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
      final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper();
      for (FacetEditorTab editorTab : myEditorTabs) {
        tabbedPane.addTab(editorTab.getDisplayName(), editorTab.getIcon(), editorTab.createComponent(), null);
      }
      tabbedPane.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          myEditorTabs[mySelectedTabIndex].onTabLeaving();
          mySelectedTabIndex = tabbedPane.getSelectedIndex();
          onTabSelected(myEditorTabs[mySelectedTabIndex]);
        }
      });
      editorComponent = tabbedPane.getComponent();
      myTabbedPane = tabbedPane;
    }
    else if (myEditorTabs.length == 1) {
      editorComponent = myEditorTabs[0].createComponent();
    }
    else {
      editorComponent = new JPanel();
    }
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(BorderLayout.CENTER, editorComponent);
    panel.add(BorderLayout.SOUTH, myErrorPanel.getComponent());

    return panel;
  }

  private void onTabSelected(final FacetEditorTab selectedTab) {
    selectedTab.onTabEntering();
    if (myVisitedTabs.add(selectedTab)) {
      final JComponent preferredFocusedComponent = selectedTab.getPreferredFocusedComponent();
      if (preferredFocusedComponent != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (preferredFocusedComponent.isShowing()) {
              preferredFocusedComponent.requestFocus();
            }
          }
        });
      }
    }
  }

  public void disposeUIResources() {
    myErrorPanel.disposeUIResources();
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

  public void onFacetSelected() {
    onTabSelected(myEditorTabs[mySelectedTabIndex]);
  }
}
