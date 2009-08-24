package com.jetbrains.python.refactoring.introduce;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 18, 2009
 * Time: 8:43:28 PM
 */
public class PyIntroduceDialog extends DialogWrapper implements PyIntroduceSettings {
  private JPanel myContentPane;
  private JLabel myErrorLabel;
  private JLabel myNameLabel;
  private ComboBox myNameComboBox;
  private JCheckBox myCheckBox;

  private final Project myProject;
  private final int myOccurrencesCount;
  private final IntroduceValidator myValidator;
  private final PyExpression myExpression;

  public PyIntroduceDialog(@NotNull final Project project,
                           @NotNull PyExpression expression,
                           @NotNull final String caption,
                           @NotNull final IntroduceValidator validator,
                           final int occurrencesCount,
                           final String[] possibleNames) {
    super(project, true);
    myOccurrencesCount = occurrencesCount;
    myValidator = validator;
    myProject = project;
    myExpression = expression;
    setUpNameComboBox(possibleNames);

    setModal(true);
    setTitle(caption);
    init();
    setupDialog();
    updateControls();
  }

  private void setUpNameComboBox(String[] possibleNames) {
    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, PythonFileType.INSTANCE, myNameComboBox);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));
    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);

    myNameComboBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateControls();
      }
    });

    ((EditorTextField)myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        updateControls();
      }
    });

    myContentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNameComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (String possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }
  }

  private void setupDialog() {
    myCheckBox.setMnemonic(KeyEvent.VK_A);
    myNameLabel.setLabelFor(myNameComboBox);

    // Replace occurences check box setup
    if (myOccurrencesCount > 1) {
      myCheckBox.setSelected(false);
      myCheckBox.setEnabled(true);
      myCheckBox.setText(myCheckBox.getText() + " (" + myOccurrencesCount + " occurrences)");
    }
    else {
      myCheckBox.setSelected(false);
      myCheckBox.setEnabled(false);
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

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
    return myCheckBox.isSelected();
  }

  private void updateControls() {
    final boolean nameValid = myValidator.isNameValid(this);
    setOKActionEnabled(nameValid);
    if (!nameValid) {
      myErrorLabel.setText(PyBundle.message("refactoring.introduce.name.error"));
      return;
    }
    final String error = myValidator.check(this);
    myErrorLabel.setText(error != null ? error : " ");
  }
}
