package com.jetbrains.edu.coursecreator.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.coursecreator.CCLanguageManager;
import com.jetbrains.edu.coursecreator.CCUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class CreateTaskFileDialog extends DialogWrapper {
  private final Course myCourse;
  private JPanel myPanel;
  private JBList myList;
  private JTextField myTextField;

  @SuppressWarnings("unchecked")
  public CreateTaskFileDialog(@Nullable Project project, String generatedFileName, @NotNull final Course course) {
    super(project);
    myCourse = course;
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

    CCLanguageManager manager = CCUtils.getStudyLanguageManager(myCourse);
    if (manager != null) {
      String extension = manager.getDefaultTaskFileExtension();
      ScrollingUtil.selectItem(myList, FileTypeManager.getInstance().getFileTypeByExtension(extension != null ? extension : "txt"));
    }
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
