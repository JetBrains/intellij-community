package com.jetbrains.edu.learning.ui;

import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HideableDecorator;
import com.intellij.util.Consumer;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.stepic.CourseInfo;
import com.jetbrains.edu.stepic.EduStepicConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * author: liana
 * data: 7/31/14.
 */
public class StudyNewProjectPanel{
  private final HideableDecorator myDecorator;
  private List<CourseInfo> myAvailableCourses = new ArrayList<CourseInfo>();
  private JButton myBrowseButton;
  private JComboBox myCoursesComboBox;
  private JButton myRefreshButton;
  private JPanel myContentPanel;
  private JLabel myAuthorLabel;
  private JLabel myDescriptionLabel;
  private JLabel myLabel;
  private JPanel myInfoPanel;
  private JPanel myHideablePanel;
  private JPanel myLoginPanel;
  private JPasswordField myPasswordField;
  private JTextField myLoginField;
  private JButton myLogInButton;
  private final StudyProjectGenerator myGenerator;
  private static final String CONNECTION_ERROR = "<html>Failed to download courses.<br>Check your Internet connection.</html>";
  private static final String INVALID_COURSE = "Selected course is invalid";
  private FacetValidatorsManager myValidationManager;

  public StudyNewProjectPanel(StudyProjectGenerator generator) {
    myGenerator = generator;
    myAvailableCourses = myGenerator.getCourses(false);
    if (myAvailableCourses.isEmpty()) {
      setError(CONNECTION_ERROR);
    }
    else {
      for (CourseInfo courseInfo : myAvailableCourses) {
        myCoursesComboBox.addItem(courseInfo);
      }
      myAuthorLabel.setText("Author: " + Course.getAuthorsString(StudyUtils.getFirst(myAvailableCourses).getInstructors()));
      myDescriptionLabel.setText(StudyUtils.getFirst(myAvailableCourses).getDescription());
      //setting the first course in list as selected
      myGenerator.setSelectedCourse(StudyUtils.getFirst(myAvailableCourses));
      setOK();
    }
    initListeners();
    myRefreshButton.setVisible(true);
    myRefreshButton.setIcon(AllIcons.Actions.Refresh);

    myLabel.setPreferredSize(new JLabel("Project name").getPreferredSize());
    myDecorator = new HideableDecorator(myHideablePanel, "Credentials", false);
    myDecorator.setOn(false);
    myDecorator.setContentComponent(myLoginPanel);

  }

  private void initListeners() {
    final FileChooserDescriptor fileChooser = new FileChooserDescriptor(true, false, false, true, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return file.isDirectory() || StudyUtils.isZip(file.getName());
      }

      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return StudyUtils.isZip(file.getName());
      }
    };
    myBrowseButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FileChooser.chooseFile(fileChooser, null, null,
                               new Consumer<VirtualFile>() {
                                 @Override
                                 public void consume(VirtualFile file) {
                                   String fileName = file.getPath();
                                   int oldSize = myAvailableCourses.size();
                                   CourseInfo courseInfo = myGenerator.addLocalCourse(fileName);
                                   if (courseInfo != null) {
                                     if (oldSize != myAvailableCourses.size()) {
                                       myCoursesComboBox.addItem(courseInfo);
                                     }
                                     myCoursesComboBox.setSelectedItem(courseInfo);
                                     setOK();
                                   }
                                   else {
                                     setError(INVALID_COURSE);
                                     myCoursesComboBox.removeAllItems();
                                     myCoursesComboBox.addItem(CourseInfo.INVALID_COURSE);
                                     for (CourseInfo course : myAvailableCourses) {
                                       myCoursesComboBox.addItem(course);
                                     }
                                     myCoursesComboBox.setSelectedItem(CourseInfo.INVALID_COURSE);
                                   }
                                 }
                               });
      }
    });

    myRefreshButton.addActionListener(new RefreshActionListener());
    myCoursesComboBox.addActionListener(new CourseSelectedListener());
    myLogInButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean isSuccess = EduStepicConnector.login(myLoginField.getText(), String.valueOf(myPasswordField.getPassword()));
        if (!isSuccess) {
          setError("Failed to log in");
          return;
        }
        refreshCoursesList();
      }
    });
  }

  private void setError(@NotNull final String errorMessage) {
    myGenerator.fireStateChanged(new ValidationResult(errorMessage));
    if (myValidationManager != null) {
      myValidationManager.validate();
    }
  }

  private void setOK() {
    myGenerator.fireStateChanged(ValidationResult.OK);
    if (myValidationManager != null) {
      myValidationManager.validate();
    }
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  public void registerValidators(final FacetValidatorsManager manager) {
    myValidationManager = manager;
  }


  /**
   * Handles refreshing courses
   * Old courses added to new courses only if their
   * meta file still exists in local file system
   */
  private class RefreshActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      refreshCoursesList();
    }
  }

  private void refreshCoursesList() {
    final List<CourseInfo> courses = myGenerator.getCourses(true);
    if (courses.isEmpty()) {
      setError(CONNECTION_ERROR);
      return;
    }
    myCoursesComboBox.removeAllItems();

    for (CourseInfo courseInfo : courses) {
      myCoursesComboBox.addItem(courseInfo);
    }
    myGenerator.setSelectedCourse(StudyUtils.getFirst(courses));

    myGenerator.setCourses(courses);
    myAvailableCourses = courses;
    myGenerator.flushCache();
  }


  /**
   * Handles selecting course in combo box
   * Sets selected course in combo box as selected in
   * {@link StudyNewProjectPanel#myGenerator}
   */
  private class CourseSelectedListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      JComboBox cb = (JComboBox)e.getSource();
      CourseInfo selectedCourse = (CourseInfo)cb.getSelectedItem();
      if (selectedCourse == null || selectedCourse.equals(CourseInfo.INVALID_COURSE)) {
        myAuthorLabel.setText("");
        myDescriptionLabel.setText("");
        return;
      }
      myAuthorLabel.setText("Author: " + Course.getAuthorsString(selectedCourse.getInstructors()));
      myCoursesComboBox.removeItem(CourseInfo.INVALID_COURSE);
      myDescriptionLabel.setText(selectedCourse.getDescription());
      myGenerator.setSelectedCourse(selectedCourse);
      setOK();
    }
  }

  public JComboBox getCoursesComboBox() {
    return myCoursesComboBox;
  }

  public JPanel getInfoPanel() {
    return myInfoPanel;
  }
}
