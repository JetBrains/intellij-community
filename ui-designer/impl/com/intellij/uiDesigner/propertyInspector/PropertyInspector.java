package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;import com.intellij.uiDesigner.radComponents.RowColumnPropertiesPanel;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.GridCaptionPanel;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;import javax.swing.event.ChangeListener;import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class PropertyInspector extends JPanel{
  private final PropertyInspectorTable myInspectorTable;
  private final ComponentTree myComponentTree;
  private final QuickFixManager myQuickFixManager;
  private GuiEditor myEditor;
  private PropertyInspector.MyComponentSelectionListener myComponentSelectionListener;
  @NonNls private static final String INSPECTOR_CARD = "inspector";
  @NonNls private static final String EMPTY_CARD = "empty";
  @NonNls private static final String COLUMN_CARD = "column";
  private RowColumnPropertiesPanel myColumnPropertiesPanel;
  private ChangeListener myColumnPropertiesChangeListener;
  private RadContainer myPropertiesPanelContainer;

  public PropertyInspector(Project project, @NotNull final ComponentTree componentTree) {
    super(new CardLayout());

    myInspectorTable = new PropertyInspectorTable(project, componentTree);
    myComponentTree = componentTree;

    // Card with property inspector
    final JPanel inspectorCard = new JPanel(new GridBagLayout());
    inspectorCard.add(
      ScrollPaneFactory.createScrollPane(myInspectorTable),
      new GridBagConstraints(0, 0, 0, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0)
    );
    final JCheckBox chkShowExpertProperties = new JCheckBox(UIDesignerBundle.message("chk.show.expert.properties"));
    inspectorCard.add(
      chkShowExpertProperties,
      new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0)
    );
    chkShowExpertProperties.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myInspectorTable.setShowExpertProperties(chkShowExpertProperties.isSelected());
        }
      }
    );
    add(inspectorCard, INSPECTOR_CARD);

    // Empty card
    final MultiLineLabel label = new MultiLineLabel(UIDesignerBundle.message("label.select.single.component.to.edit.its.properties")){
      public void updateUI() {
        super.updateUI();
        setBackground(myInspectorTable.getBackground());
      }
    };
    label.setOpaque(true);
    label.setHorizontalAlignment(SwingConstants.CENTER);
    add(label, EMPTY_CARD);

    myComponentSelectionListener = new MyComponentSelectionListener();
    synchWithTree(false);

    // Install light bulb
    myQuickFixManager = new QuickFixManagerImpl(null, myInspectorTable);

    myColumnPropertiesChangeListener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myPropertiesPanelContainer.revalidate();
        myEditor.refreshAndSave(true);
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
    }
  }

  public void refreshIntentionHint() {
    myQuickFixManager.refreshIntentionHint();
  }

  public void synchWithTree(final boolean forceSynch) {
    final CardLayout cardLayout = (CardLayout)getLayout();
    if (!showSelectedColumnProperties()) {
      final RadComponent[] selectedComponents = myComponentTree.getSelectedComponents();
      if(selectedComponents.length >= 1){
        cardLayout.show(this, INSPECTOR_CARD);
        myInspectorTable.synchWithTree(forceSynch);
      }
      else{
        cardLayout.show(this, EMPTY_CARD);
      }
    }
  }

  private boolean showSelectedColumnProperties() {
    if (myColumnPropertiesPanel != null && IJSwingUtilities.hasFocus(myColumnPropertiesPanel.getComponent())) {
      return true;
    }
    if (myEditor == null) return false;
    GridCaptionPanel panel = myEditor.getFocusedCaptionPanel();
    if (panel == null) return false;
    RadContainer container = panel.getSelectedContainer();
    if (container == null) return false;
    final int[] selection = panel.getSelectedCells(null);
    myPropertiesPanelContainer = container;
    final RowColumnPropertiesPanel propertiesPanel = container.getLayoutManager().getRowColumnPropertiesPanel(container, panel.isRow(), selection);
    if (propertiesPanel == null) return false;
    if (!Comparing.equal(propertiesPanel, myColumnPropertiesPanel)) {
      if (myColumnPropertiesPanel != null) {
        remove(myColumnPropertiesPanel.getComponent());
        myColumnPropertiesPanel.removeChangeListener(myColumnPropertiesChangeListener);
      }
      myColumnPropertiesPanel = propertiesPanel;
      myColumnPropertiesPanel.addChangeListener(myColumnPropertiesChangeListener);
      add(myColumnPropertiesPanel.getComponent(), COLUMN_CARD);
    }
    final CardLayout cardLayout = (CardLayout)getLayout();
    cardLayout.show(this, COLUMN_CARD);
    return true;
  }

  public boolean isEditing(){
    return myInspectorTable.isEditing();
  }

  public void stopEditing(){
    myInspectorTable.editingStopped(null);
  }

  public void requestFocus() {
    myInspectorTable.requestFocus();
  }

  /**
   * Synchronizes state with component which is selected in the ComponentTree
   */
  private final class MyComponentSelectionListener implements ComponentSelectionListener{
    public void selectedComponentChanged(final GuiEditor source){
      synchWithTree(false);
    }
  }
}