// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.introduce;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.EnumSet;

public class PyIntroduceDialog extends DialogWrapper {
  private JPanel myContentPane;
  private JLabel myNameLabel;
  private ComboBox myNameComboBox;
  private JCheckBox myReplaceAll;
  private JRadioButton mySamePlace;
  private JRadioButton myConstructor;
  private JRadioButton mySetUp;
  private JPanel myPlaceSelector;

  private final Project myProject;
  private final int myOccurrencesCount;
  private final IntroduceValidator myValidator;
  private final PyExpression myExpression;
  private final String myHelpId;

  public PyIntroduceDialog(@NotNull final Project project,
                           @NotNull final @DialogTitle String caption,
                           @NotNull final IntroduceValidator validator,
                           final String helpId,
                           final IntroduceOperation operation) {
    super(project, true);
    myOccurrencesCount = operation.getOccurrences().size();
    myValidator = validator;
    myProject = project;
    myExpression = operation.getInitializer();
    myHelpId = helpId;
    setUpNameComboBox(operation.getSuggestedNames());

    setTitle(caption);
    init();
    setupDialog(operation.getAvailableInitPlaces());
    updateControls();
  }

  @Override
  protected String getHelpId() {
    return myHelpId;
  }

  private void setUpNameComboBox(Collection<String> possibleNames) {
    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, PythonFileType.INSTANCE, myNameComboBox);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));
    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);

    myNameComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateControls();
      }
    });

    ((EditorTextField)myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        updateControls();
      }
    });

    myContentPane.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myNameComboBox, true));
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (@Nls String possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }
  }

  private void setupDialog(EnumSet<IntroduceHandler.InitPlace> availableInitPlaces) {
    myReplaceAll.setMnemonic(KeyEvent.VK_A);
    myNameLabel.setLabelFor(myNameComboBox);
    myPlaceSelector.setVisible(availableInitPlaces.size() > 1);
    myConstructor.setVisible(availableInitPlaces.contains(IntroduceHandler.InitPlace.CONSTRUCTOR));
    mySetUp.setVisible(availableInitPlaces.contains(IntroduceHandler.InitPlace.SET_UP));
    mySamePlace.setSelected(true);

    // Replace occurrences check box setup
    if (myOccurrencesCount > 1) {
      myReplaceAll.setSelected(false);
      myReplaceAll.setEnabled(true);
      myReplaceAll.setText(PyBundle.message("refactoring.occurrences.count", myReplaceAll.getText(), myOccurrencesCount));
    }
    else {
      myReplaceAll.setSelected(false);
      myReplaceAll.setEnabled(false);
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @Nullable
  public String getName() {
    final Object item = myNameComboBox.getEditor().getItem();
    if ((item instanceof String) && ((String)item).length() > 0) {
      return ((String)item).trim();
    }
    return null;
  }

  public Project getProject() {
    return myProject;
  }

  public PyExpression getExpression() {
    return myExpression;
  }

  public boolean doReplaceAllOccurrences() {
    return myReplaceAll.isSelected();
  }

  private void updateControls() {
    final boolean nameValid = myValidator.isNameValid(getName(), getProject());
    setOKActionEnabled(nameValid);
    setErrorText(!nameValid ? PyPsiBundle.message("refactoring.introduce.name.error") : null, myNameComboBox);
  }

  public IntroduceHandler.InitPlace getInitPlace() {
    if (myConstructor.isSelected()) return IntroduceHandler.InitPlace.CONSTRUCTOR;
    if (mySetUp.isSelected())  return IntroduceHandler.InitPlace.SET_UP;
    return IntroduceHandler.InitPlace.SAME_METHOD;
  }
}
