package com.jetbrains.edu.coursecreator.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.MouseEvent;

public class CreateTaskFileDialog extends DialogWrapper {
  private JPanel myPanel;
  private JBList myList;
  private JTextField myTextField;

  @SuppressWarnings("unchecked")
  public CreateTaskFileDialog(@Nullable Project project, String generatedFileName) {
    super(project);
    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();

    DefaultListModel model = new DefaultListModel();
    for (FileType type : fileTypes) {
      if (!type.isReadOnly() && !type.getDefaultExtension().isEmpty()) {
        model.addElement(type);
      }
    }
    myList.setModel(model);
    myTextField.setText(generatedFileName);
    setTitle("Create New Task File");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new FileTypeRenderer());

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        doOKAction();
        return true;
      }
    }.installOn(myList);

    myList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          //TODO: do smth to check validness
        }
      }
    );

    ListScrollingUtil.selectItem(myList, FileTypeManager.getInstance().getFileTypeByExtension("py"));
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTextField;
  }

  public String getFileName() {
    return myTextField.getText();
  }

  public FileType getFileType() {
    return (FileType)myList.getSelectedValue();
  }
}
