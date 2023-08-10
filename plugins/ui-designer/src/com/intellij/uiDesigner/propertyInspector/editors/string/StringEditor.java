// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class StringEditor extends PropertyEditor<StringDescriptor> {
  private static final Logger LOG = Logger.getInstance(StringEditor.class);

  @Nullable private final IntroStringProperty myProperty;
  private final TextFieldWithBrowseButton myTfWithButton;
  /* Initial value of string property that was passed into getComponent() method */
  private StringDescriptor myValue;
  private final Project myProject;
  private RadComponent myComponent;
  private boolean myTextFieldModified = false;

  public StringEditor(Project project) {
    this(project, null);
  }

  public StringEditor(Project project, final IntroStringProperty property) {
    myProject = project;
    myProperty = property;
    myTfWithButton = new TextFieldWithBrowseButton(new MyActionListener());
    myTfWithButton.getTextField().setBorder(null);

    final JTextField textField = myTfWithButton.getTextField();
    textField.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          fireValueCommitted(false, false);
        }
      }
    );
    textField.getDocument().addDocumentListener(
      new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull final DocumentEvent e) {
          // Order of document listeners invocation is not defined in Swing. In practice, custom listeners like this one are invoked
          // before internal JTextField listeners, so at this point the internal state of JTextField can be inconsistent.
          // That's the reason for using 'invokeLater' here.
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> preferredSizeChanged());
          myTextFieldModified = true;
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

  @Override
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
        textField.selectAll();
        myTextFieldModified = false;
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

  @Override
  public JComponent getPreferredFocusedComponent(@NotNull final JComponent component) {
    return ((TextFieldWithBrowseButton)component).getTextField();
  }

  @Override
  public JComponent getComponent(final RadComponent component, final StringDescriptor value, final InplaceContext inplaceContext) {
    myComponent = component;
    setValue(value);

    myTfWithButton.getTextField().setBorder(null);
    if (inplaceContext != null && inplaceContext.isStartedByTyping()) {
      myTfWithButton.setText(Character.toString(inplaceContext.getStartChar()));
    }

    return myTfWithButton;
  }

  @Override
  public StringDescriptor getValue(){
    if(myValue == null || (myValue.getValue() != null && myTextFieldModified)) {
      // editor is for "trivial" StringDescriptor
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
    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      fireEditingCancelled();
    }
  }

  private final class MyActionListener implements ActionListener{
    @Override
    public void actionPerformed(final ActionEvent e) {
      // 1. Show editor dialog

      final GuiEditor guiEditor = DesignerToolWindowManager.getInstance(myProject).getActiveFormEditor();
      LOG.assertTrue(guiEditor != null);

      final StringEditorDialog dialog = new StringEditorDialog(
        myTfWithButton.getTextField(),
        getValue(), // we have pass here "live" (modified) value
        guiEditor.getStringDescriptorLocale(),
        guiEditor
      );

      CommandProcessor.getInstance().executeCommand(
        myProject,
        () -> {
          if (!guiEditor.ensureEditable()) {
            return;
          }
          if (!dialog.showAndGet()) {
            return;
          }

          // 2. Apply new value
          final StringDescriptor descriptor = dialog.getDescriptor();
          if (descriptor == null) {
            return;
          }
          setValue(descriptor);
          fireValueCommitted(true, false);
          if (myProperty != null) {
            myProperty.refreshValue(myComponent);
          }
          guiEditor.refreshAndSave(false);
        }, UIDesignerBundle.message("command.edit.string.property"), null);
    }
  }
}