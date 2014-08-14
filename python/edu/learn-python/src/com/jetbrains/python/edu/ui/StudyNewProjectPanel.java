package com.jetbrains.python.edu.ui;

import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.jetbrains.python.edu.StudyDirectoryProjectGenerator;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.CourseInfo;
import icons.StudyIcons;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * author: liana
 * data: 7/31/14.
 */
public class StudyNewProjectPanel{
  private Set<CourseInfo> myAvailableCourses = new HashSet<CourseInfo>();
  private JComboBox myCoursesComboBox;
  private JButton myBrowseButton;
  private JButton myRefreshButton;
  private JPanel myContentPanel;
  private JLabel myAuthorLabel;
  private JLabel myDescriptionLabel;
  private final StudyDirectoryProjectGenerator myGenerator;
  private static final String CONNECTION_ERROR = "<html>Failed to download courses.<br>Check your Internet connection.</html>";
  private static final String INVALID_COURSE = "Selected course is invalid";
  private FacetValidatorsManager myValidationManager;

  public StudyNewProjectPanel(StudyDirectoryProjectGenerator generator) {
    myGenerator = generator;
    Map<CourseInfo, File> courses = myGenerator.getCourses();
    if (courses.isEmpty()) {
      setError(CONNECTION_ERROR);
    }
    else {
      myAvailableCourses = courses.keySet();
      for (CourseInfo courseInfo : myAvailableCourses) {
        myCoursesComboBox.addItem(courseInfo);
      }
      myAuthorLabel.setText("Author: " + StudyUtils.getFirst(myAvailableCourses).getAuthor());
      myDescriptionLabel.setText(StudyUtils.getFirst(myAvailableCourses).getDescription());
      //setting the first course in list as selected
      myGenerator.setSelectedCourse(StudyUtils.getFirst(myAvailableCourses));
      setOK();
    }
    initListeners();
    myRefreshButton.setVisible(true);
    myRefreshButton.setIcon(StudyIcons.Refresh);
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
                                   if (courseInfo != null)  {
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
  }

  private void setError(String errorMessage) {
    myGenerator.setValidationResult(new ValidationResult(errorMessage));
    if (myValidationManager != null) {
      myValidationManager.validate();
    }
  }

  private void setOK() {
    myGenerator.setValidationResult(ValidationResult.OK);
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
      myGenerator.downloadAndUnzip(true);
      Map<CourseInfo, File> downloadedCourses = myGenerator.loadCourses();
      if (downloadedCourses.isEmpty()) {
        setError(CONNECTION_ERROR);
        return;
      }
      Map<CourseInfo, File> oldCourses = myGenerator.getLoadedCourses();
      Map<CourseInfo, File> newCourses = new HashMap<CourseInfo, File>();
      for (Map.Entry<CourseInfo, File> course : oldCourses.entrySet()) {
        File courseFile = course.getValue();
        if (courseFile.exists()) {
          newCourses.put(course.getKey(), courseFile);
        }
      }
      for (Map.Entry<CourseInfo, File> course : downloadedCourses.entrySet()) {
        CourseInfo courseName = course.getKey();
        if (newCourses.get(courseName) == null) {
          newCourses.put(courseName, course.getValue());
        }
      }
      myCoursesComboBox.removeAllItems();

      for (CourseInfo courseInfo : newCourses.keySet()) {
        myCoursesComboBox.addItem(courseInfo);
      }
      myGenerator.setSelectedCourse(StudyUtils.getFirst(newCourses.keySet()));

      myGenerator.setCourses(newCourses);
      myAvailableCourses = newCourses.keySet();
      myGenerator.flushCache();
    }
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
      myAuthorLabel.setText("Author: " + selectedCourse.getAuthor());
      myCoursesComboBox.removeItem(CourseInfo.INVALID_COURSE);
      myDescriptionLabel.setText(selectedCourse.getDescription());
      myGenerator.setSelectedCourse(selectedCourse);
      setOK();
    }
  }
}
