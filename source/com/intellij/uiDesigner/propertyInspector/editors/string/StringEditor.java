package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StringEditor extends PropertyEditor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor");

  private final TextFieldWithBrowseButton myTfWithButton;
  /* Initial value of string property that was passed into getComponent() method */
  private StringDescriptor myValue;

  public StringEditor(){
    myTfWithButton = new TextFieldWithBrowseButton(new MyActionListener());
    myTfWithButton.getTextField().setBorder(null);

    final JTextField textField = myTfWithButton.getTextField();
    textField.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          fireValueCommited();
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
        textField.setBackground(UIManager.getColor("TextField.background"));
        textField.setText("[" + descriptor.getKey() + " / " + descriptor.getBundleName().replace('/', '.') + "]");
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

  public JComponent getComponent(final RadComponent component, final Object value, final boolean inplace){
    final StringDescriptor descriptor = (StringDescriptor)value;
    setValue(descriptor);

    myTfWithButton.getTextField().setBorder(null);

    return myTfWithButton;
  }

  public Object getValue(){
    if(myValue == null || myValue.getValue() != null){ // editor is for "trivial" StringDescriptor
      final String value = myTfWithButton.getText();
      if (myValue == null && value.length() == 0) {
        return null;
      }
      else{
        return StringDescriptor.create(value);
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

      final DataContext dataContext = DataManager.getInstance().getDataContext(myTfWithButton.getTextField());
      final UIFormEditor editor = (UIFormEditor)dataContext.getData(DataConstants.FILE_EDITOR);
      final Module module = editor.getEditor().getModule();

      final StringEditorDialog dialog = new StringEditorDialog(
        myTfWithButton.getTextField(),
        (StringDescriptor)getValue(), // we have pass here "live" (modified) value
        module
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
      fireValueCommited();
    }
  }
}