package com.jetbrains.edu.learning.newproject.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import com.jetbrains.edu.learning.stepic.EduStepicAuthorizedClient;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EduCoursesPanel extends JPanel {
  private static final JBColor LIST_COLOR = new JBColor(Gray.xFF, Gray.x39);
  public static final Color COLOR = new Color(70, 130, 180, 70);
  private JPanel myMainPanel;
  private JPanel myCourseListPanel;
  private JPanel myCoursePanel;
  private JPanel myAdvancedSettingsPlaceholder;
  private JPanel myAdvancedSettings;
  private FilterComponent mySearchField;
  private JEditorPane myDescriptionTextArea;
  private JBLabel myCourseNameLabel;
  private JPanel myTagsPanel;
  private JBScrollPane myInfoScroll;
  private JBLabel myErrorLabel;
  private JSplitPane mySplitPane;
  private JBList<Course> myCoursesList;
  private LabeledComponent<TextFieldWithBrowseButton> myLocationField;
  private List<Course> myCourses;
  private List<CourseValidationListener> myListeners = new ArrayList<>();

  public EduCoursesPanel() {
    setLayout(new BorderLayout());
    add(myMainPanel, BorderLayout.CENTER);
    initUI();
  }


  private void initUI() {
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myMainPanel, true);
    mySplitPane.setDividerLocation(0.4);
    myCourseNameLabel.setBorder(IdeBorderFactory.createEmptyBorder(20, 10, 5, 10));
    Font labelFont = UIUtil.getLabelFont();
    myCourseNameLabel.setFont(new Font(labelFont.getName(), Font.BOLD, JBUI.scaleFontSize(18.0f)));
    myTagsPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 10, 0, 10));
    myDescriptionTextArea.setBorder(IdeBorderFactory.createEmptyBorder(20, 10, 10, 10));
    myDescriptionTextArea.setEditorKit(UIUtil.getHTMLEditorKit());
    myDescriptionTextArea.setEditable(false);
    myDescriptionTextArea.setPreferredSize(JBUI.size(myCoursePanel.getPreferredSize()));
    myInfoScroll.setBorder(null);
    myCoursesList = new JBList<>();
    myCourses = getCourses();
    updateModel(myCourses);
    myErrorLabel.setVisible(false);
    myErrorLabel.setBorder(IdeBorderFactory.createEmptyBorder(20, 10, 0, 0));

    ListCellRendererWrapper<Course> renderer = new ListCellRendererWrapper<Course>() {
      @Override
      public void customize(JList list, Course value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
        DirectoryProjectGenerator generator = getGenerator(value);
        if (generator != null) {
          boolean isPrivate = value instanceof RemoteCourse && !((RemoteCourse)value).isPublic();
          setIcon(isPrivate ? getPrivateCourseIcon(generator.getLogo()) : generator.getLogo());
          setToolTipText(isPrivate ? "Private course" : "");
        }
      }

      @NotNull
      public LayeredIcon getPrivateCourseIcon(@Nullable Icon languageLogo) {
        LayeredIcon icon = new LayeredIcon(2);
        icon.setIcon(languageLogo, 0, 0, 0);
        icon.setIcon(AllIcons.Ide.Readonly, 1, JBUI.scale(7), JBUI.scale(7));
        return icon;
      }
    };
    myCoursesList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component component = renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (component instanceof JLabel) {
          ((JLabel)component).setBorder(IdeBorderFactory.createEmptyBorder(5, 0, 5, 0));
        }
        return component;
      }
    });
    myLocationField = createLocationComponent();
    myCoursesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        Course selectedCourse = myCoursesList.getSelectedValue();
        if (selectedCourse == null) {
          notifyListeners(false);
          return;
        }
        updateCourseInfoPanel(selectedCourse);
        updateAdvancedSettings(selectedCourse);
        if (!isLoggedIn()) {
          myErrorLabel.setVisible(true);
          myErrorLabel.setText(UIUtil.toHtml("<u><b>Log in</b></u> " + (selectedCourse.isAdaptive() ? "to start adaptive course" :"to see more courses")));
          myErrorLabel.setForeground((selectedCourse.isAdaptive() ? MessageType.ERROR : MessageType.WARNING).getTitleForeground());
        }
        notifyListeners(!selectedCourse.isAdaptive());
      }
    });
    DefaultActionGroup group = new DefaultActionGroup(new AnAction("Import Course", "import local course", AllIcons.ToolbarDecorator.Import) {
      @Override
      public void actionPerformed(AnActionEvent e) {
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
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        FileChooser.chooseFile(fileChooser, null, projects.length == 0 ? null : projects[0].getBaseDir(),
                               file -> {
                                 String fileName = file.getPath();
                                 Course course = new StudyProjectGenerator().addLocalCourse(fileName);
                                 if (course != null) {
                                   myCourses.add(course);
                                   updateModel(myCourses);
                                   myCoursesList.setSelectedValue(course, true);
                                 }
                               });
      }
    });

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myCoursesList).
      disableAddAction().disableRemoveAction().disableUpDownActions().setActionGroup(group);
    JPanel toolbarDecoratorPanel = toolbarDecorator.createPanel();
    toolbarDecoratorPanel.setBorder(null);
    myCoursesList.setBorder(null);
    myCourseListPanel.add(toolbarDecoratorPanel, BorderLayout.CENTER);
    Border border = JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, 0, 1, 1);
    myCoursePanel.setBorder(border);
    myCourseListPanel.setBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, 1, 1, 1));
    HideableDecorator decorator = new HideableDecorator(myAdvancedSettingsPlaceholder, "Advanced Settings", false);
    decorator.setContentComponent(myAdvancedSettings);
    myAdvancedSettings.setBorder(IdeBorderFactory.createEmptyBorder(0, IdeBorderFactory.TITLED_BORDER_INDENT, 5, 0));
    UIUtil.setBackgroundRecursively(myCoursesList, LIST_COLOR);
    myDescriptionTextArea.setBackground(UIUtil.getPanelBackground());
    myAdvancedSettingsPlaceholder.setVisible(false);
    myErrorLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!isLoggedIn() && myErrorLabel.isVisible()) {
          StepicUser user = EduStepicAuthorizedClient.showLoginDialog();
          if (user != null) {
            StudySettings.getInstance().setUser(user);
            myCourses = getCourses();
            updateModel(myCourses);
            myErrorLabel.setVisible(false);
            notifyListeners(true);
          }
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (!isLoggedIn() && myErrorLabel.isVisible()) {
          e.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (!isLoggedIn() && myErrorLabel.isVisible()) {
          e.getComponent().setCursor(Cursor.getDefaultCursor());
        }
      }
    });
  }

  @NotNull
  private static List<Course> getCourses() {
    return new StudyProjectGenerator().getCoursesUnderProgress(true, "Getting Available Courses", null);
  }

  private static boolean isLoggedIn() {
    return StudySettings.getInstance().getUser() != null;
  }

  private void updateAdvancedSettings(Course selectedCourse) {
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
    LabeledComponent<JComponent> component = generator.getLanguageSettingsComponent(selectedCourse);
    if (component == null) {
      return;
    }
    myAdvancedSettings.removeAll();
    myAdvancedSettings.add(myLocationField, BorderLayout.NORTH);
    myAdvancedSettings.add(component, BorderLayout.SOUTH);
    UIUtil.mergeComponentsWithAnchor(myLocationField, component);
    myAdvancedSettings.revalidate();
    myAdvancedSettings.repaint();
  }

  private void updateCourseInfoPanel(Course selectedCourse) {
    String courseName = selectedCourse.getName();
    String description = selectedCourse.getDescription();
    myCourseNameLabel.setText(courseName);
    myTagsPanel.removeAll();
    addTags(myTagsPanel, selectedCourse);
    myTagsPanel.revalidate();
    myTagsPanel.repaint();
    StringBuilder builder = new StringBuilder();
    List<StepicUser> authors = selectedCourse.getAuthors();
    if (!authors.isEmpty()) {
      builder.append("<b>Instructor");
      if (authors.size() > 1) {
        builder.append("s");
      }
      builder.append("</b>: ");
      List<String> fullNames = getAuthorFullNames(authors);
      builder.append(StringUtil.join(fullNames, ", "));
      builder.append("<br><br>");
    }
    builder.append(description.replace("\n", "<br>"));
    myDescriptionTextArea.setText(UIUtil.toHtml(builder.toString()));
  }

  private static List<String> getAuthorFullNames(List<StepicUser> authors) {
    return authors.stream().
          map(user -> StringUtil.join(Arrays.asList(user.getFirstName(), user.getLastName()), " "))
          .collect(Collectors.toList());
  }

  private static void addTags(JPanel tagsPanel, @NotNull Course course) {
    for (String tag : getTags(course)) {
      tagsPanel.add(createTagLabel(tag));
    }
  }

  private static JLabel createTagLabel(String tagText) {
    Border emptyBorder = IdeBorderFactory.createEmptyBorder(3, 5, 3, 5);
    JBLabel label = new JBLabel(tagText);
    label.setOpaque(true);
    label.setBorder(emptyBorder);
    label.setBackground(new JBColor(COLOR, COLOR));
    return label;
  }

  private void updateModel(List<Course> courses) {
    DefaultListModel<Course> listModel = new DefaultListModel<>();
    for (Course course : courses) {
      listModel.addElement(course);
    }
    myCoursesList.setModel(listModel);
    if (myCoursesList.getItemsCount() > 0) {
      myCoursesList.setSelectedIndex(0);
    }
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
    if (filter.isEmpty()) {
      return true;
    }
    filter = filter.toLowerCase();
    if (course.getName().toLowerCase().contains(filter)) {
      return true;
    }
    for (String tag : getTags(course)) {
      if (tag.toLowerCase().contains(filter)) {
        return true;
      }
    }
    for (String authorName : getAuthorFullNames(course.getAuthors())) {
      if (authorName.toLowerCase().contains(filter)) {
        return true;
      }
    }
    return false;
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

  @Override
  public Dimension getPreferredSize() {
    return JBUI.size(600, 400);
  }

  public void addCourseValidationListener(CourseValidationListener listener) {
    myListeners.add(listener);
  }

  private void notifyListeners(boolean canStartCourse) {
    for (CourseValidationListener listener : myListeners) {
      listener.validationStatusChanged(canStartCourse);
    }
  }

  public interface CourseValidationListener {
    void validationStatusChanged(boolean canStartCourse);
  }

  private static List<String> getTags(@NotNull Course course) {
    List<String> tags = new ArrayList<>();
    tags.add(course.getLanguageById().getDisplayName());
    if (course.isAdaptive()) {
      tags.add("Adaptive");
    }
    return tags;
  }
}
