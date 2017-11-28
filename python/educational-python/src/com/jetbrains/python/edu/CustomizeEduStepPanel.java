package com.jetbrains.python.edu;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.util.ui.JBUI;
import icons.PythonEducationalIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class CustomizeEduStepPanel extends JPanel {
  static final String COURSE_CREATOR_ENABLED = "Edu.CourseCreator.Enabled";
  private final JLabel myStudent;

  public CustomizeEduStepPanel() {
    final FlowLayout layout = new FlowLayout();
    layout.setHgap(20);
    setLayout(layout);
    int iconSize = JBUI.scale(180);
    final JPanel studentPanel = new JPanel(new VerticalFlowLayout());
    final JLabel teacher = new JLabel(PythonEducationalIcons.Teacher);
    myStudent = new JLabel(PythonEducationalIcons.StudentHover);
    myStudent.setPreferredSize(new Dimension(iconSize, iconSize));
    myStudent.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        PropertiesComponent.getInstance().setValue(COURSE_CREATOR_ENABLED, false);
        myStudent.setIcon(PythonEducationalIcons.StudentHover);
        teacher.setIcon(PythonEducationalIcons.Teacher);
      }
    });
    studentPanel.add(myStudent);
    studentPanel.add(new JLabel("Student", SwingConstants.CENTER));
    add(studentPanel);

    teacher.setPreferredSize(new Dimension(iconSize, iconSize));
    final JPanel teacherPanel = new JPanel(new VerticalFlowLayout());
    teacherPanel.add(teacher);
    teacherPanel.add(new JLabel("Teacher", SwingConstants.CENTER));
    teacher.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        PropertiesComponent.getInstance().setValue(COURSE_CREATOR_ENABLED, true);
        myStudent.setIcon(PythonEducationalIcons.Student);
        teacher.setIcon(PythonEducationalIcons.TeacherHover);
      }
    });
    add(teacherPanel);
  }

  public JComponent getStudentButton() {
    return myStudent;
  }
}
