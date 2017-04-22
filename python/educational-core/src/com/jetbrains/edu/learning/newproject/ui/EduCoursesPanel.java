package com.jetbrains.edu.learning.newproject.ui;

import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;

public class EduCoursesPanel extends JPanel {
  private JPanel myMainPanel;
  private JEditorPane myDescriptionTextArea;
  private JPanel myCourseListPanel;
  private JPanel myInfoPanel;
  private JBScrollPane myDescriptionScrollPane;
  private JBList<Course> myCoursesList;

  public EduCoursesPanel() {
    setLayout(new BorderLayout());
    add(myMainPanel, BorderLayout.CENTER);
    initUI();
  }


  private void initUI() {
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myMainPanel, true);
    myDescriptionTextArea.setEditable(false);
    DefaultListModel<Course> listModel = new DefaultListModel<>();
    List<Course> courses = new StudyProjectGenerator().getCoursesUnderProgress(true, "Getting courses", null);
    for (Course course : courses) {
      listModel.addElement(course);
    }
    myCoursesList = new JBList<>(listModel);
    myCoursesList.setCellRenderer(new DefaultListCellRenderer());
    myCoursesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        Course selectedCourse = myCoursesList.getSelectedValue();
        myDescriptionTextArea.setText(selectedCourse.getDescription());
      }
    });
    JScrollPane installedScrollPane = ScrollPaneFactory.createScrollPane(myCoursesList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                         ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myCourseListPanel.add(installedScrollPane, BorderLayout.CENTER);
    myDescriptionScrollPane.setBackground(UIUtil.getTextFieldBackground());
    Border border = JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, 0, 1, 1);
    myInfoPanel.setBorder(border);
  }

  public Course getSelectedCourse() {
    return myCoursesList.getSelectedValue();
  }
}
