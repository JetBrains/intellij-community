package com.jetbrains.edu.learning.newproject.ui;

import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;

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
  private JPanel myAdvancedSettingsPlaceholder;
  private JPanel myAdvancedSettings;
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
    List<Course> courses = new StudyProjectGenerator().getCoursesUnderProgress(true, "Getting Available Courses", null);
    for (Course course : courses) {
      listModel.addElement(course);
    }
    myCoursesList = new JBList<>(listModel);
    myCoursesList.setCellRenderer(new ListCellRendererWrapper<Course>() {
      @Override
      public void customize(JList list, Course value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
        EduCourseProjectGenerator projectGenerator =
          EduPluginConfigurator.INSTANCE.forLanguage(value.getLanguageById()).getEduCourseProjectGenerator();
        if (projectGenerator != null) {
          setIcon(projectGenerator.getDirectoryProjectGenerator().getLogo());
        }
      }
    });
    JBLabel label = new JBLabel();
    myCoursesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        Course selectedCourse = myCoursesList.getSelectedValue();
        myDescriptionTextArea.setText(selectedCourse.getDescription());
        label.setText(selectedCourse.getName());
        myAdvancedSettingsPlaceholder.setVisible(true);
      }
    });
    JScrollPane installedScrollPane = ScrollPaneFactory.createScrollPane(myCoursesList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                         ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myCourseListPanel.add(installedScrollPane, BorderLayout.CENTER);
    myDescriptionScrollPane.setBackground(UIUtil.getTextFieldBackground());
    Border border = JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, 0, 1, 1);
    myInfoPanel.setBorder(border);
    HideableDecorator decorator = new HideableDecorator(myAdvancedSettingsPlaceholder, "Advanced Settings", false);
    decorator.setContentComponent(myAdvancedSettings);
    myAdvancedSettings.setBorder(IdeBorderFactory.createEmptyBorder(0, IdeBorderFactory.TITLED_BORDER_INDENT, 5, 0));
    UIUtil.setBackgroundRecursively(myAdvancedSettingsPlaceholder, UIUtil.getTextFieldBackground());
    myAdvancedSettings.setLayout(new BorderLayout());
    myAdvancedSettings.add(label);
    myAdvancedSettingsPlaceholder.setVisible(false);
  }

  public Course getSelectedCourse() {
    return myCoursesList.getSelectedValue();
  }
}
