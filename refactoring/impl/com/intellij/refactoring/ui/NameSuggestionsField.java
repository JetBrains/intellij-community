package com.intellij.refactoring.ui;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

public class NameSuggestionsField extends JPanel {
  private final JComponent myComponent;
  private final EventListenerList myListenerList = new EventListenerList();
  private final MyComboBoxModel myComboBoxModel;
  private final Project myProject;

  public NameSuggestionsField(Project project) {
    super(new BorderLayout());
    myProject = project;
    myComboBoxModel = new MyComboBoxModel();
    final ComboBox comboBox = new ComboBox(myComboBoxModel,-1);
    myComponent = comboBox;
    add(myComponent, BorderLayout.CENTER);
    setupComboBox(comboBox, StdFileTypes.JAVA);
  }

  public NameSuggestionsField(String[] nameSuggestions, Project project) {
    this(nameSuggestions, project, StdFileTypes.JAVA);
  }

  public NameSuggestionsField(String[] nameSuggestions, Project project, FileType fileType) {
    super(new BorderLayout());
    myProject = project;
    if (nameSuggestions == null || nameSuggestions.length <= 1) {
      myComponent = createTextFieldForName(nameSuggestions, fileType);
    }
    else {
      final ComboBox combobox = new ComboBox(nameSuggestions,-1);
      combobox.setSelectedIndex(0);
      setupComboBox(combobox, fileType);
      myComponent = combobox;
    }
    add(myComponent, BorderLayout.CENTER);
    myComboBoxModel = null;
  }

  public NameSuggestionsField(final String[] suggestedNames, final Project project, final FileType fileType, @Nullable final Editor editor) {
    this(suggestedNames, project, fileType);
    if (editor != null) {
      // later here because EditorTextField creates Editor during addNotify()
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          final int offset = editor.getCaretModel().getOffset();
          List<TextRange> ranges = new ArrayList<TextRange>();
          SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editor.getDocument().getCharsSequence(), offset, ranges);
          Editor myEditor = getEditor();
          if (myEditor == null) return;
          for (TextRange wordRange : ranges) {
            String word = editor.getDocument().getText().substring(wordRange.getStartOffset(), wordRange.getEndOffset());
            if (word.equals(getName())) {
              final SelectionModel selectionModel = editor.getSelectionModel();
              final TextRange selected = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()).shiftRight(-wordRange.getStartOffset());
              myEditor.getSelectionModel().removeSelection();
              int myOffset = offset - wordRange.getStartOffset();
              myEditor.getCaretModel().moveToOffset(myOffset);
              if (selectionModel.hasSelection()) {
                myEditor.getSelectionModel().setSelection(selected.getStartOffset(), selected.getEndOffset());
              }
              else {
                myEditor.getSelectionModel().setSelection(0, myEditor.getDocument().getTextLength());
              }
              break;
            }
          }
        }
      });
    }
  }

  public void setSuggestions(final String[] suggestions) {
    if(myComboBoxModel == null) return;
    JComboBox comboBox = (JComboBox) myComponent;
    final String oldSelectedItem = (String)comboBox.getSelectedItem();
    final String oldItemFromTextField = (String) comboBox.getEditor().getItem();
    final boolean shouldUpdateTextField =
      oldItemFromTextField.equals(oldSelectedItem) || oldItemFromTextField.trim().length() == 0;
    myComboBoxModel.setSuggestions(suggestions);
    if(suggestions.length > 0 && shouldUpdateTextField) {
      comboBox.setSelectedIndex(0);
    }
    else {
      comboBox.getEditor().setItem(oldItemFromTextField);
    }
  }

  public JComponent getComponent() {
    return this;
  }

  public JComponent getFocusableComponent() {
    if(myComponent instanceof JComboBox) {
      return (JComponent) ((JComboBox) myComponent).getEditor().getEditorComponent();
    } else {
      return myComponent;
    }
  }

  public String getName() {
    if (myComponent instanceof JComboBox) {
      return (String)((JComboBox)myComponent).getEditor().getItem();
    } else {
      return ((EditorTextField) myComponent).getText();
    }
  }

  private JComponent createTextFieldForName(String[] nameSuggestions, FileType fileType) {
    final String text;
    if (nameSuggestions != null && nameSuggestions.length > 0 && nameSuggestions[0] != null) {
      text = nameSuggestions[0];
    }
    else {
      text = "";
    }

    EditorTextField field = new EditorTextField(text, myProject, fileType);
    field.selectAll();
    field.addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireDataChanged();
      }
    });

    return field;
  }

  private static class MyComboBoxModel extends DefaultComboBoxModel {
    private String[] mySuggestions;

    MyComboBoxModel() {
      mySuggestions = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    // implements javax.swing.ListModel
    public int getSize() {
      return mySuggestions.length;
    }

    // implements javax.swing.ListModel
    public Object getElementAt(int index) {
      return mySuggestions[index];
    }

    public void setSuggestions(String[] suggestions) {
      fireIntervalRemoved(this, 0, mySuggestions.length);
      mySuggestions = suggestions;
      fireIntervalAdded(this, 0, mySuggestions.length);
    }

  }

  private void setupComboBox(final ComboBox combobox, FileType fileType) {
    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, fileType);

    combobox.setEditor(comboEditor);
    combobox.setRenderer(new EditorComboBoxRenderer(comboEditor));

    combobox.setEditable(true);
    combobox.setMaximumRowCount(8);

    combobox.addItemListener(
      new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          fireDataChanged();
        }
      }
    );


    ((EditorTextField)combobox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {

      }

      public void documentChanged(DocumentEvent event) {
        fireDataChanged();
      }
    }
    );
  }

  public Editor getEditor() {
    if (myComponent instanceof EditorTextField) {
      return ((EditorTextField)myComponent).getEditor();
    }
    else {
      return ((EditorTextField)((JComboBox)myComponent).getEditor().getEditorComponent()).getEditor();
    }
  }

  public static interface DataChanged extends EventListener {
    void dataChanged();
  }

  public void addDataChangedListener(DataChanged listener) {
    myListenerList.add(DataChanged.class, listener);
  }

  private void fireDataChanged() {
    Object[] list = myListenerList.getListenerList();

    for (Object aList : list) {
      if (aList instanceof DataChanged) {
        ((DataChanged)aList).dataChanged();
      }
    }
  }

  public boolean requestFocusInWindow() {
    if(myComponent instanceof JComboBox) {
      return ((JComboBox) myComponent).getEditor().getEditorComponent().requestFocusInWindow();
    }
    else {
      return myComponent.requestFocusInWindow();
    }
  }

  public void setEnabled (boolean enabled) {
    myComponent.setEnabled(enabled);
  }
}
