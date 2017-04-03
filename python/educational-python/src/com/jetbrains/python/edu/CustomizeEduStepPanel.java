package com.jetbrains.python.edu;

import com.intellij.ide.customize.AbstractCustomizeWizardStep;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.edu.learning.StudySettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CustomizeEduStepPanel extends AbstractCustomizeWizardStep {

  public CustomizeEduStepPanel() {
    final JPanel studentPanel = new JPanel(new VerticalFlowLayout());
    final JButton student = new JButton(IconLoader.findIcon("/icons/com/jetbrains/edu/student.png"));
    student.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        StudySettings.getInstance().setCourseCreatorEnabled(false);
      }
    });
    studentPanel.add(student);
    studentPanel.add(new JLabel("Student", SwingConstants.CENTER));
    add(studentPanel);

    final JButton teacher = new JButton(IconLoader.findIcon("/icons/com/jetbrains/edu/teacher.png"));
    final JPanel teacherPanel = new JPanel(new VerticalFlowLayout());
    teacherPanel.add(teacher);
    teacherPanel.add(new JLabel("Teacher", SwingConstants.CENTER));
    teacher.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        StudySettings.getInstance().setCourseCreatorEnabled(true);
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
