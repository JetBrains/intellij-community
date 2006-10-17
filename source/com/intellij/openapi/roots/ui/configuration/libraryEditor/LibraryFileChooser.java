package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FieldPanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;

public class LibraryFileChooser extends FileChooserDialogImpl {
  private JTextField myNameField;
  private final boolean myInputName;
  private final LibraryTableEditor myParentEditor;
  private boolean myNameChangedByUser = false;

  public LibraryFileChooser(FileChooserDescriptor chooserDescriptor,
                            Component parent,
                            boolean inputName,
                            LibraryTableEditor parentEditor) {
    super(chooserDescriptor, parent);
    myInputName = inputName;
    myParentEditor = parentEditor;
  }

  public String getName() {
    if (myNameField != null) {
      final String name = myNameField.getText().trim();
      return name.length() > 0 ? name : null;
    }
    return null;
  }

  private void setName(String name) {
    if (myNameField != null) {
      final boolean savedValue = myNameChangedByUser;
      try {
        myNameField.setText(name);
      }
      finally {
        myNameChangedByUser = savedValue;
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myInputName ? myNameField : super.getPreferredFocusedComponent();
  }

  protected JComponent createCenterPanel() {
    final JComponent centerPanel = super.createCenterPanel();
    if (!myInputName) {
      return centerPanel;
    }

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(centerPanel, BorderLayout.CENTER);

    final FieldPanel fieldPanel = FieldPanel.create(ProjectBundle.message("library.name.prompt"), null);
    fieldPanel.getFieldLabel().setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myNameField = fieldPanel.getTextField();
    myNameField.getDocument().addDocumentListener(new DocumentListener() {
      public void changedUpdate(DocumentEvent e) {
        myNameChangedByUser = true;
      }

      public void insertUpdate(DocumentEvent e) {
        myNameChangedByUser = true;
      }

      public void removeUpdate(DocumentEvent e) {
        myNameChangedByUser = true;
      }
    });
    panel.add(fieldPanel, BorderLayout.NORTH);

    myFileSystemTree.getTree().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (myNameField == null || myNameChangedByUser) {
          return;
        }
        final VirtualFile[] selectedFiles = getSelectedFiles();
        setName(selectedFiles.length == 1 ? selectedFiles[0].getNameWithoutExtension() : "");
      }
    });
    return panel;
  }

  protected void doOKAction() {
    if (!validateData()) {
      return;
    }

    super.doOKAction();
  }

  private boolean validateData() {
    JComponent componentToFocus = null;
    try {
      final VirtualFile[] chosenFiles = getSelectedFiles();
      if (chosenFiles != null && chosenFiles.length > 0) {
        if (myInputName) {
          final String name = getName();
          if (name == null) {
            Messages.showErrorDialog(myNameField, ProjectBundle.message("library.name.not.specified.error"),
                                     ProjectBundle.message("library.name.not.specified.title"));
            componentToFocus = myNameField;
            return false;
          }
          if (myParentEditor.libraryAlreadyExists(name)) {
            Messages.showErrorDialog(myNameField, ProjectBundle.message("library.name.already.exists.error", name),
                                     ProjectBundle.message("library.name.already.exists.title"));
            componentToFocus = myNameField;
            return false;
          }
        }
      }
      else {
        Messages.showErrorDialog(ProjectBundle.message("library.files.not.selected.error"),
                                 ProjectBundle.message("library.files.not.selected.title"));
        componentToFocus = myFileSystemTree.getTree();
        return false;
      }
      return true;
    }
    finally {
      if (componentToFocus != null) {
        final JComponent _componentToFocus = componentToFocus;
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            _componentToFocus.requestFocus();
          }
        });
      }
    }
  }

  public Pair<String, VirtualFile[]> chooseNameAndFiles(@Nullable VirtualFile toSelect) {
    VirtualFile[] chosenFiles = choose(toSelect, null);
    return new Pair<String, VirtualFile[]>(getName(), chosenFiles);
  }

  public Pair<String, VirtualFile[]> chooseNameAndFiles() {
    return chooseNameAndFiles(null);
  }
}
