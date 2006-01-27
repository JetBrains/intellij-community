package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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

  public PropertyInspector(Project project, final GuiEditor editor, @NotNull final ComponentTree componentTree) {
    super(new CardLayout());

    myInspectorTable = new PropertyInspectorTable(project, editor, componentTree);
    myComponentTree = componentTree;
    myEditor = editor;

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
    //noinspection HardCodedStringLiteral
    add(inspectorCard, "inspector");

    // Empty card
    final MultiLineLabel label = new MultiLineLabel(UIDesignerBundle.message("label.select.single.component.to.edit.its.properties")){
      public void updateUI() {
        super.updateUI();
        setBackground(myInspectorTable.getBackground());
      }
    };
    label.setOpaque(true);
    label.setHorizontalAlignment(SwingConstants.CENTER);
    //noinspection HardCodedStringLiteral
    add(label, "empty");

    myComponentSelectionListener = new MyComponentSelectionListener();
    if (editor != null) {
      editor.addComponentSelectionListener(myComponentSelectionListener);
    }
    synchWithTree(false);

    // Install light bulb
    myQuickFixManager = new QuickFixManagerImpl(editor, myInspectorTable);
  }

  public void setEditor(final GuiEditor editor) {
    if (myEditor != editor) {
      if (myEditor != null) {
        myEditor.removeComponentSelectionLsistener(myComponentSelectionListener);
      }
      myEditor = editor;
      myInspectorTable.setEditor(myEditor);
      myQuickFixManager.setEditor(myEditor);
      if (myEditor != null) {
        myEditor.addComponentSelectionListener(myComponentSelectionListener);
      }
    }
  }

  public void updateIntentionHintVisibility(){
    myQuickFixManager.updateIntentionHintVisibility();
  }

  /**
   * Hides intention hint (if any)
   */
  public void hideIntentionHint(){
    myQuickFixManager.hideIntentionHint();
  }

  public void synchWithTree(final boolean forceSynch){
    final RadComponent[] selectedComponents = myComponentTree.getSelectedComponents();
    final CardLayout cardLayout = (CardLayout)getLayout();
    if(selectedComponents.length == 1){
      //noinspection HardCodedStringLiteral
      cardLayout.show(this, "inspector");
      myInspectorTable.synchWithTree(forceSynch);
    }
    else{
      //noinspection HardCodedStringLiteral
      cardLayout.show(this, "empty");
    }
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