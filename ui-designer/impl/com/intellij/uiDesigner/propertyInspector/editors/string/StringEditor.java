package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StringEditor extends PropertyEditor<StringDescriptor> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor");

  private final TextFieldWithBrowseButton myTfWithButton;
  /* Initial value of string property that was passed into getComponent() method */
  private StringDescriptor myValue;
  private Project myProject;

  public StringEditor(Project project){
    myProject = project;
    myTfWithButton = new TextFieldWithBrowseButton(new MyActionListener());
    myTfWithButton.getTextField().setBorder(null);

    final JTextField textField = myTfWithButton.getTextField();
    textField.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          fireValueCommited(false);
        }
      }
    );
    textField.getDocument().addDocumentListener(
      new DocumentAdapter() {
        protected void textChanged(final DocumentEvent e) {
          preferredSizeChanged();
        }
      }
    );

    final MyCancelEditingAction cancelEditingAction = new MyCancelEditingAction();
    cancelEditingAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, myTfWithButton);
  }

  /**
   * @return current preferred size of the editor component
   */
  public Dimension getPreferredSize(){
    return myTfWithButton.getPreferredSize();
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTfWithButton);
  }

  /**
   * Applies specified bundle to the myTfWithBrowseButton
   */
  private void setValue(final StringDescriptor descriptor){
    myValue = descriptor;
    final JTextField textField = myTfWithButton.getTextField();
    if(descriptor != null){
      final String value = descriptor.getValue();
      if(value != null){ // plain value
        textField.setEditable(true);
        textField.setText(value);
      }
      else{ // bundled value
        textField.setEditable(false);
        textField.setBackground(UIUtil.getTextFieldBackground());
        textField.setText("[" + descriptor.getKey() + " / " + descriptor.getDottedBundleName() + "]");
      }
    }
    else{
      textField.setEditable(true);
      textField.setText(null);
    }
  }

  public JComponent getPreferredFocusedComponent(final JComponent component) {
    LOG.assertTrue(component != null);
    return ((TextFieldWithBrowseButton)component).getTextField();
  }

  public JComponent getComponent(final RadComponent component, final StringDescriptor value, final boolean inplace){
    setValue(value);

    myTfWithButton.getTextField().setBorder(null);

    return myTfWithButton;
  }

  public StringDescriptor getValue(){
    if(myValue == null || myValue.getValue() != null){ // editor is for "trivial" StringDescriptor
      final String value = myTfWithButton.getText();
      if (myValue == null && value.length() == 0) {
        return null;
      }
      else{
        final StringDescriptor stringDescriptor = StringDescriptor.create(value);
        if (myValue != null && myValue.isNoI18n()) {
          stringDescriptor.setNoI18n(true);
        }
        return stringDescriptor;
      }
    }
    else{ // editor is for "bundled" StringDescriptor
      return myValue;
    }
  }

  private final class MyCancelEditingAction extends AnAction{
    public void actionPerformed(final AnActionEvent e) {
      fireEditingCancelled();
    }
  }

  private final class MyActionListener implements ActionListener{
    public void actionPerformed(final ActionEvent e) {
      // 1. Show editor dialog

      final GuiEditor guiEditor = UIDesignerToolWindowManager.getInstance(myProject).getActiveFormEditor();
      LOG.assertTrue(guiEditor != null);
      final Module module = guiEditor.getModule();

      final StringEditorDialog dialog = new StringEditorDialog(
        myTfWithButton.getTextField(),
        getValue(), // we have pass here "live" (modified) value
        guiEditor.getStringDescriptorLocale(),
        guiEditor
      );
      dialog.show();
      if(!dialog.isOK()){
        return;
      }

      // 2. Apply new value
      final StringDescriptor descriptor = dialog.getDescriptor();
      if(descriptor == null){
        return;
      }
      setValue(descriptor);
      fireValueCommited(true);
    }
  }
}