// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.designSurface.GridCaptionPanel;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;
import com.intellij.uiDesigner.radComponents.*;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public final class PropertyInspector extends JPanel{
  private final PropertyInspectorTable myInspectorTable;
  private final ComponentTree myComponentTree;
  private final QuickFixManager myQuickFixManager;
  private GuiEditor myEditor;
  private final PropertyInspector.MyComponentSelectionListener myComponentSelectionListener;
  private static final @NonNls String INSPECTOR_CARD = "inspector";
  private static final @NonNls String EMPTY_CARD = "empty";
  private static final @NonNls String CUSTOM_CARD = "column";
  private final JScrollPane myCustomPropertiesScrollPane = ScrollPaneFactory.createScrollPane();
  private CustomPropertiesPanel myCustomPropertiesPanel;
  private final ChangeListener myCustomPropertiesChangeListener;
  private RadContainer myPropertiesPanelContainer;

  public PropertyInspector(Project project, final @NotNull ComponentTree componentTree) {
    super(new CardLayout());

    myInspectorTable = new PropertyInspectorTable(project, componentTree);
    myComponentTree = componentTree;

    // Card with property inspector
    final JPanel inspectorCard = new JPanel(new GridBagLayout());
    final JScrollPane inspectorScrollPane = ScrollPaneFactory.createScrollPane(myInspectorTable);
    inspectorScrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    inspectorCard.add(inspectorScrollPane,
      new GridBagConstraints(0, 0, 0, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0)
    );
    final JCheckBox chkShowExpertProperties = new JCheckBox(UIDesignerBundle.message("chk.show.expert.properties"));
    inspectorCard.add(
      chkShowExpertProperties,
      new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0)
    );
    chkShowExpertProperties.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          myInspectorTable.setShowExpertProperties(chkShowExpertProperties.isSelected());
        }
      }
    );
    add(inspectorCard, INSPECTOR_CARD);

    // Empty card
    final MultiLineLabel label = new MultiLineLabel(UIDesignerBundle.message("label.select.single.component.to.edit.its.properties")){
      @Override
      public void updateUI() {
        super.updateUI();
        setBackground(myInspectorTable.getBackground());
      }
    };
    label.setOpaque(true);
    label.setHorizontalAlignment(SwingConstants.CENTER);
    add(label, EMPTY_CARD);
    add(myCustomPropertiesScrollPane, CUSTOM_CARD);

    myComponentSelectionListener = new MyComponentSelectionListener();
    synchWithTree(false);

    // Install light bulb
    myQuickFixManager = new QuickFixManagerImpl(null, myInspectorTable, inspectorScrollPane.getViewport());

    myCustomPropertiesChangeListener = new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (myPropertiesPanelContainer != null) {
          myPropertiesPanelContainer.revalidate();
        }
        if (myEditor.ensureEditable()) {
          myEditor.refreshAndSave(true);
        }
      }
    };
  }

  public void setEditor(final GuiEditor editor) {
    if (myEditor != editor) {
      if (myEditor != null) {
        myEditor.removeComponentSelectionListener(myComponentSelectionListener);
      }
      myEditor = editor;
      myInspectorTable.setEditor(myEditor);
      myQuickFixManager.setEditor(myEditor);
      if (myEditor != null) {
        myEditor.addComponentSelectionListener(myComponentSelectionListener);
      }
      else {
        if (myCustomPropertiesPanel != null) {
          myCustomPropertiesPanel.removeChangeListener(myCustomPropertiesChangeListener);
        }
      }
    }
  }

  public void refreshIntentionHint() {
    myQuickFixManager.refreshIntentionHint();
  }

  public void synchWithTree(final boolean forceSynch) {
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701")) {
      final CardLayout cardLayout = (CardLayout)getLayout();
      if (!showSelectedColumnProperties()) {
        final RadComponent[] selectedComponents = myComponentTree.getSelectedComponents();
        if (selectedComponents.length >= 1) {
          cardLayout.show(this, INSPECTOR_CARD);
          myInspectorTable.synchWithTree(forceSynch);
        }
        else {
          List<RadButtonGroup> buttonGroups = myComponentTree.getSelectedElements(RadButtonGroup.class);
          if (buttonGroups.size() > 0) {
            showButtonGroupProperties(buttonGroups.get(0));
          }
          else {
            cardLayout.show(this, EMPTY_CARD);
          }
        }
      }
    }
  }

  private void showButtonGroupProperties(final RadButtonGroup group) {
    ButtonGroupPropertiesPanel props = new ButtonGroupPropertiesPanel(myEditor.getRootContainer(), group);
    myPropertiesPanelContainer = null;
    showCustomPropertiesPanel(props);
  }

  private boolean showSelectedColumnProperties() {
    if (myCustomPropertiesPanel != null && myPropertiesPanelContainer != null &&
        IJSwingUtilities.hasFocus(myCustomPropertiesPanel.getComponent())) {
      return true;
    }
    if (myEditor == null) return false;
    GridCaptionPanel panel = myEditor.getFocusedCaptionPanel();
    if (panel == null) return false;
    RadContainer container = panel.getSelectedContainer();
    if (container == null) return false;
    final int[] selection = panel.getSelectedCells(null);
    myPropertiesPanelContainer = container;
    final CustomPropertiesPanel propertiesPanel = container.getGridLayoutManager().getRowColumnPropertiesPanel(container, panel.isRow(), selection);
    if (propertiesPanel == null) return false;
    showCustomPropertiesPanel(propertiesPanel);
    return true;
  }

  private void showCustomPropertiesPanel(final CustomPropertiesPanel propertiesPanel) {
    if (!Comparing.equal(propertiesPanel, myCustomPropertiesPanel)) {
      if (myCustomPropertiesPanel != null) {
        myCustomPropertiesPanel.removeChangeListener(myCustomPropertiesChangeListener);
      }
      myCustomPropertiesPanel = propertiesPanel;
      myCustomPropertiesPanel.addChangeListener(myCustomPropertiesChangeListener);
      myCustomPropertiesScrollPane.getViewport().setView(myCustomPropertiesPanel.getComponent());
    }
    final CardLayout cardLayout = (CardLayout)getLayout();
    cardLayout.show(this, CUSTOM_CARD);
  }

  public boolean isEditing(){
    return myInspectorTable.isEditing();
  }

  public void stopEditing(){
    myInspectorTable.editingStopped(null);
  }

  @Override
  public void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myInspectorTable, true));
  }

  /**
   * Synchronizes state with component which is selected in the ComponentTree
   */
  private final class MyComponentSelectionListener implements ComponentSelectionListener{
    @Override
    public void selectedComponentChanged(final @NotNull GuiEditor source){
      synchWithTree(false);
    }
  }
}
