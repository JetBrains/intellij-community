package com.jetbrains.edu.learning.newproject.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
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
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class EduCoursesPanel extends JPanel {
  private static final Set<String> FEATURED_COURSES = ContainerUtil.newHashSet("Introduction to Python", "Adaptive Python");
  private static final JBColor LIST_COLOR = new JBColor(Gray.xFF, Gray.x39);
  public static final Color COLOR = new Color(70, 130, 180, 70);
  private static final Logger LOG = Logger.getInstance(EduCoursesPanel.class);
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
  private JPanel mySplitPaneRoot;
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
    GuiUtils.replaceJSplitPaneWithIDEASplitter(mySplitPaneRoot, true);
    mySplitPane.setDividerLocation(0.5);
    mySplitPane.setResizeWeight(0.5);
    myCourseNameLabel.setBorder(JBUI.Borders.empty(20, 10, 5, 10));
    Font labelFont = UIUtil.getLabelFont();
    myCourseNameLabel.setFont(new Font(labelFont.getName(), Font.BOLD, JBUI.scaleFontSize(18.0f)));
    myTagsPanel.setBorder(JBUI.Borders.empty(0, 10));
    myDescriptionTextArea.setBorder(JBUI.Borders.empty(20, 10, 10, 10));
    myDescriptionTextArea.setEditorKit(UIUtil.getHTMLEditorKit());
    myDescriptionTextArea.setEditable(false);
    myDescriptionTextArea.setPreferredSize(JBUI.size(myCoursePanel.getPreferredSize()));
    myInfoScroll.setBorder(null);
    myCoursesList = new JBList<>();
    myCourses = getCourses();
    updateModel(myCourses, null);
    myErrorLabel.setVisible(false);
    myErrorLabel.setBorder(JBUI.Borders.empty(20, 10, 0, 0));

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
          ((JLabel)component).setBorder(JBUI.Borders.empty(5, 0));
        }
        return component;
      }
    });
    myLocationField = createLocationComponent();
    myCoursesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        processSelectionChanged();
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
        FileChooser.chooseFile(fileChooser, null, VfsUtil.getUserHomeDir(),
                               file -> {
                                 String fileName = file.getPath();
                                 Course course = new StudyProjectGenerator().addLocalCourse(fileName);
                                 if (course != null) {
                                   myCourses.add(course);
                                   updateModel(myCourses, course.getName());
                                   EduUsagesCollector.localCourseAdded();
                                 } else {
                                   Messages.showErrorDialog("Selected archive doesn't contain a valid course", "Failed to Add Local Course");
                                 }
                               });
      }
    });

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myCoursesList).
      disableAddAction().disableRemoveAction().disableUpDownActions().setActionGroup(group).setToolbarPosition(ActionToolbarPosition.BOTTOM);
    JPanel toolbarDecoratorPanel = toolbarDecorator.createPanel();
    toolbarDecoratorPanel.setBorder(null);
    myCoursesList.setBorder(null);
    myCourseListPanel.add(toolbarDecoratorPanel, BorderLayout.CENTER);
    Border border = JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, 0, 1, 1);
    myCoursePanel.setBorder(border);
    myCourseListPanel.setBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 1, 1, 1, 1));
    HideableDecorator decorator = new HideableDecorator(myAdvancedSettingsPlaceholder, "Advanced Settings", false);
    decorator.setContentComponent(myAdvancedSettings);
    myAdvancedSettings.setBorder(JBUI.Borders.empty(0, IdeBorderFactory.TITLED_BORDER_INDENT, 5, 0));
    myCoursesList.setBackground(LIST_COLOR);
    myDescriptionTextArea.setBackground(UIUtil.getPanelBackground());
    myAdvancedSettingsPlaceholder.setVisible(false);
    myErrorLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!isLoggedIn() && myErrorLabel.isVisible()) {
          ApplicationManager.getApplication().getMessageBus().connect().subscribe(StudySettings.SETTINGS_CHANGED, () -> {
            StepicUser user = StudySettings.getInstance().getUser();
            if (user != null) {
              ApplicationManager.getApplication().invokeLater(() -> {
                Course selectedCourse = myCoursesList.getSelectedValue();
                myCourses = getCourses();
                updateModel(myCourses, selectedCourse.getName());
                myErrorLabel.setVisible(false);
                notifyListeners(true);
              }, ModalityState.any());
            }
          });
          EduStepicConnector.doAuthorize(() -> StudyUtils.showOAuthDialog());
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

    processSelectionChanged();
  }

  private void processSelectionChanged() {
    Course selectedCourse = myCoursesList.getSelectedValue();
    notifyListeners(canStartCourse(selectedCourse));
    if (selectedCourse != null) {
      updateCourseInfoPanel(selectedCourse);
    }
  }

  private void updateCourseInfoPanel(Course selectedCourse) {
    updateCourseDescriptionPanel(selectedCourse);
    updateAdvancedSettings(selectedCourse);
    if (!isLoggedIn()) {
      myErrorLabel.setVisible(true);
      myErrorLabel.setText(
        UIUtil.toHtml("<u><b>Log in</b></u> to Stepik " + (selectedCourse.isAdaptive() ? "to start adaptive course" : "to see more courses")));
      myErrorLabel.setForeground((selectedCourse.isAdaptive() ? MessageType.ERROR : MessageType.WARNING).getTitleForeground());
    }
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
    myAdvancedSettings.removeAll();
    myAdvancedSettings.add(myLocationField, BorderLayout.NORTH);
    if (component != null) {
      myAdvancedSettings.add(component, BorderLayout.SOUTH);
      UIUtil.mergeComponentsWithAnchor(myLocationField, component);
    }
    myAdvancedSettings.revalidate();
    myAdvancedSettings.repaint();
  }

  private void updateCourseDescriptionPanel(@Nullable Course selectedCourse) {
    if (selectedCourse == null) {
      myInfoScroll.setVisible(false);
      myAdvancedSettingsPlaceholder.setVisible(false);
      return;
    }
    myInfoScroll.setVisible(true);
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
    Border emptyBorder = JBUI.Borders.empty(3, 5);
    JBLabel label = new JBLabel(tagText);
    label.setOpaque(true);
    label.setBorder(emptyBorder);
    label.setBackground(new JBColor(COLOR, COLOR));
    return label;
  }

  private static int getWeight(@NotNull Course course) {
    String name = course.getName();
    if (FEATURED_COURSES.contains(name)) {
      return FEATURED_COURSES.size() - 1 - new ArrayList<>(FEATURED_COURSES).indexOf(name);
    }
    return FEATURED_COURSES.size();
  }

  private void updateModel(List<Course> courses, @Nullable String courseToSelect) {
    DefaultListModel<Course> listModel = new DefaultListModel<>();
    Collections.sort(courses, Comparator.comparingInt(EduCoursesPanel::getWeight));
    for (Course course : courses) {
      listModel.addElement(course);
    }
    myCoursesList.setModel(listModel);
    if (myCoursesList.getItemsCount() > 0) {
      myCoursesList.setSelectedIndex(0);
    } else {
      updateCourseDescriptionPanel(null);
    }
    if (courseToSelect == null) {
      return;
    }
    Course newCourseToSelect = myCourses.stream().filter(course -> course.getName().equals(courseToSelect)).findFirst().orElse(null);
    if (newCourseToSelect != null ) {
      myCoursesList.setSelectedValue(newCourseToSelect, true);
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
        updateModel(filtered, null);
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
    if (projectGenerator == null) {
      LOG.info("project generator is null, language: " + course.getLanguageById().getDisplayName());
    }
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
    listener.validationStatusChanged(canStartCourse(myCoursesList.getSelectedValue()));
  }

  private void notifyListeners(boolean canStartCourse) {
    for (CourseValidationListener listener : myListeners) {
      listener.validationStatusChanged(canStartCourse);
    }
  }

  private static boolean canStartCourse(Course selectedCourse) {
    if (selectedCourse == null) {
      return false;
    }

    if (isLoggedIn()) {
      return true;
    }

    return !selectedCourse.isAdaptive();
  }

  public interface CourseValidationListener {
    void validationStatusChanged(boolean canStartCourse);
  }

  private static List<String> getTags(@NotNull Course course) {
    List<String> tags = new ArrayList<>();
    tags.add(course.getLanguageById().getDisplayName());
    if (course.isAdaptive()) {
      tags.add(EduNames.ADAPTIVE);
    }
    return tags;
  }
}
