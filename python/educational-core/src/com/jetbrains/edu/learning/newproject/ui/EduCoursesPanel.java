package com.jetbrains.edu.learning.newproject.ui;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EduCoursesPanel extends JPanel {
  private static final JBColor LIST_COLOR = new JBColor(Gray.xFF, Gray.x39);
  private JPanel myMainPanel;
  private JEditorPane myDescriptionTextArea;
  private JPanel myCourseListPanel;
  private JPanel myInfoPanel;
  private JBScrollPane myDescriptionScrollPane;
  private JPanel myAdvancedSettingsPlaceholder;
  private JPanel myAdvancedSettings;
  private FilterComponent mySearchField;
  private JBList<Course> myCoursesList;
  private LabeledComponent<TextFieldWithBrowseButton> myLocationField;
  private List<Course> myCourses;

  public EduCoursesPanel() {
    setLayout(new BorderLayout());
    add(myMainPanel, BorderLayout.CENTER);
    initUI();
  }


  private void initUI() {
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myMainPanel, true);
    myDescriptionTextArea.setEditable(false);
    myCoursesList = new JBList<>();
    myCourses = new StudyProjectGenerator().getCoursesUnderProgress(true, "Getting Available Courses", null);
    updateModel(myCourses);

    myCoursesList.setCellRenderer(new ListCellRendererWrapper<Course>() {
      @Override
      public void customize(JList list, Course value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
        DirectoryProjectGenerator generator = getGenerator(value);
        if (generator != null) {
          setIcon(generator.getLogo());
        }
      }
    });
    myLocationField = createLocationComponent();
    myCoursesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        Course selectedCourse = myCoursesList.getSelectedValue();
        if (selectedCourse == null) {
          return;
        }
        myDescriptionTextArea.setText(selectedCourse.getDescription());
        myAdvancedSettingsPlaceholder.setVisible(true);
        myLocationField.getComponent().setText(nameToLocation(selectedCourse.getName()));
        EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(selectedCourse.getLanguageById());
        if (configurator == null) {
          return;
        }
        EduCourseProjectGenerator generator = configurator.getEduCourseProjectGenerator();
        if (generator == null) {
          return;
        }
        LabeledComponent<JComponent> component = generator.getLanguageSettingsComponent();
        if (component == null) {
          return;
        }
        myAdvancedSettings.removeAll();
        myAdvancedSettings.add(myLocationField, BorderLayout.NORTH);
        myAdvancedSettings.add(component, BorderLayout.SOUTH);
        UIUtil.mergeComponentsWithAnchor(myLocationField, component);
      }
    });
    JScrollPane installedScrollPane = ScrollPaneFactory.createScrollPane(myCoursesList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                         ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    installedScrollPane.setBorder(null);
    myCourseListPanel.add(installedScrollPane, BorderLayout.CENTER);
    Border border = JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, 0, 1, 1);
    myInfoPanel.setBorder(border);
    myCourseListPanel.setBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, 1, 1, 1));
    HideableDecorator decorator = new HideableDecorator(myAdvancedSettingsPlaceholder, "Advanced Settings", false);
    decorator.setContentComponent(myAdvancedSettings);
    myAdvancedSettings.setBorder(IdeBorderFactory.createEmptyBorder(0, IdeBorderFactory.TITLED_BORDER_INDENT, 5, 0));
    UIUtil.setBackgroundRecursively(myCoursesList, LIST_COLOR);
    UIUtil.setBackgroundRecursively(myDescriptionScrollPane, UIUtil.getPanelBackground());
    myAdvancedSettingsPlaceholder.setVisible(false);
  }

  private void updateModel(List<Course> courses) {
    DefaultListModel<Course> listModel = new DefaultListModel<>();
    for (Course course : courses) {
      listModel.addElement(course);
    }
    myCoursesList.setModel(listModel);
  }

  public Course getSelectedCourse() {
    return myCoursesList.getSelectedValue();
  }

  private void createUIComponents() {
    mySearchField = new FilterComponent("Edu.NewCourse", 5, true) {
      @Override
      public void filter() {
        String filter = getFilter();
        List<Course> filtered = new ArrayList<>();
        for (Course course : myCourses) {
          if (accept(filter, course)) {
            filtered.add(course);
          }
        }
        updateModel(filtered);
      }
    };
    UIUtil.setBackgroundRecursively(mySearchField, UIUtil.getTextFieldBackground());
  }

  public boolean accept(String filter, Course course) {
    return course.getName().toLowerCase().contains(filter.toLowerCase()) || filter.isEmpty();
  }

  @Nullable
  private static DirectoryProjectGenerator getGenerator(@NotNull Course course) {
    EduCourseProjectGenerator projectGenerator =
      EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById()).getEduCourseProjectGenerator();
    return projectGenerator == null ? null : projectGenerator.getDirectoryProjectGenerator();
  }

  private static LabeledComponent<TextFieldWithBrowseButton> createLocationComponent() {
    TextFieldWithBrowseButton field = new TextFieldWithBrowseButton();
    field.addBrowseFolderListener("Select Course Location", "Select course location", null,
                                  FileChooserDescriptorFactory.createSingleFolderDescriptor());
    return LabeledComponent.create(field, "Location", BorderLayout.WEST);
  }

  @NotNull
  private static String nameToLocation(@NotNull String courseName) {
    String name = FileUtil.sanitizeFileName(courseName);
    return FileUtil.findSequentNonexistentFile(new File(ProjectUtil.getBaseDir()), name, "").getAbsolutePath();
  }

  public String getLocationString() {
    return myLocationField.getComponent().getText();
  }
}
