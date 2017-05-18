package com.jetbrains.python.edu;

import com.intellij.ide.customize.AbstractCustomizeWizardStep;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.VerticalFlowLayout;
import icons.PythonEducationalIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CustomizeEduStepPanel extends AbstractCustomizeWizardStep {
  static final String COURSE_CREATOR_ENABLED = "Edu.CourseCreator.Enabled";

  public CustomizeEduStepPanel() {
    int iconSize = 180;
    final JPanel studentPanel = new JPanel(new VerticalFlowLayout());
    final JButton student = new JButton(PythonEducationalIcons.Student);
    student.setPreferredSize(new Dimension(iconSize, iconSize));
    student.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(COURSE_CREATOR_ENABLED, false);
      }
    });
    studentPanel.add(student);
    studentPanel.add(new JLabel("Student", SwingConstants.CENTER));
    add(studentPanel);

    final JButton teacher = new JButton(PythonEducationalIcons.Teacher);
    teacher.setPreferredSize(new Dimension(iconSize, iconSize));
    final JPanel teacherPanel = new JPanel(new VerticalFlowLayout());
    teacherPanel.add(teacher);
    teacherPanel.add(new JLabel("Teacher", SwingConstants.CENTER));
    teacher.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(COURSE_CREATOR_ENABLED, true);
      }
    });
    add(teacherPanel);
  }

  @Override
  protected String getTitle() {
    return "Are you student or teacher?";
  }

  @Override
  protected String getHTMLHeader() {
    return "";
  }
}
