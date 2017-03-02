package com.jetbrains.edu.learning.intellij.localCourses;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.stepic.CourseInfo;
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

  public EduLocalCoursePanel(final StudyProjectGenerator generator, EduCustomCourseModuleBuilder builder) {
    myGenerator = generator;
    myBuilder = builder;
    myCourseArchivePath.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        String coursePath = myCourseArchivePath.getText();
        CourseInfo courseInfo = generator.addLocalCourse(coursePath);
        if (courseInfo != null) {
          //TODO: REMOVE THIS after API CHANGE
          myBuilder.setSelectedCourse(courseInfo);
          myGenerator.setSelectedCourse(courseInfo);
          //TODO: change API to do this!!!
//          final String authorsString = Course.getAuthorsString(courseInfo.getAuthors());
//          ((JLabel)myInfoPanel.getComponent(0)).setText(!StringUtil.isEmptyOrSpaces(authorsString) ? "Author: " + authorsString : "");
//          ((JTextPane)myInfoPanel.getComponent(1)).setText(courseInfo.getDescription());
        }
        else {
          //TODO: validate!!!
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
    StudyNewProjectPanel panel = new StudyNewProjectPanel(myGenerator);
    myInfoPanel = panel.getInfoPanel();
  }
}
