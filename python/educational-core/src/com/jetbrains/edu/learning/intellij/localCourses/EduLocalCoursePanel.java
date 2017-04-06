package com.jetbrains.edu.learning.intellij.localCourses;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.ui.StudyNewProjectPanel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class EduLocalCoursePanel {
  private final StudyProjectGenerator myGenerator;
  private EduCustomCourseModuleBuilder myBuilder;
  private JPanel myContentPanel;
  private JPanel myInfoPanel;
  private TextFieldWithBrowseButton myCourseArchivePath;
  private StudyNewProjectPanel myStudyPanel;

  public EduLocalCoursePanel(final StudyProjectGenerator generator, EduCustomCourseModuleBuilder builder) {
    myGenerator = generator;
    myBuilder = builder;
    myCourseArchivePath.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        String coursePath = myCourseArchivePath.getText();
        Course courseInfo = generator.addLocalCourse(coursePath);
        if (courseInfo != null) {
          myBuilder.setSelectedCourse(courseInfo);
          myGenerator.setSelectedCourse(courseInfo);
          myStudyPanel.updateInfoPanel(courseInfo);
        }
        else {
          //TODO: implement validation
        }
      }
    });
    myCourseArchivePath.addBrowseFolderListener("Select Course Archive", null, null,
                                                FileChooserDescriptorFactory.createSingleFileDescriptor());
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  private void createUIComponents() {
    myStudyPanel = new StudyNewProjectPanel(myGenerator, true);
    myInfoPanel = myStudyPanel.getInfoPanel();
  }
}
