package com.jetbrains.edu.learning.ui;

import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.DefaultProjectFactoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.PanelWithAnchor;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.stepic.CourseInfo;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * author: liana
 * data: 7/31/14.
 */
public class StudyNewProjectPanel extends JPanel implements PanelWithAnchor {
  private List<CourseInfo> myAvailableCourses = new ArrayList<CourseInfo>();
  private JButton myBrowseButton;
  private ComboBox<CourseInfo> myCoursesComboBox;
  private JButton myRefreshButton;
  private JLabel myAuthorLabel;
  private JPanel myInfoPanel;
  private JTextPane myDescriptionPane;
  private JComponent myAnchor;
  private final StudyProjectGenerator myGenerator;
  private static final String CONNECTION_ERROR = "<html>Failed to download courses.<br>Check your Internet connection.</html>";
  private static final String INVALID_COURSE = "Selected course is invalid";
  private FacetValidatorsManager myValidationManager;
  private boolean isComboboxInitialized;
  private static final String LOGIN_TO_STEPIC_MESSAGE = "<html><u>Login to Stepic</u> to open the adaptive course </html>";
  private static final String LOGIN_TO_STEPIC = "Login to Stepic";

  public StudyNewProjectPanel(@NotNull final StudyProjectGenerator generator) {
    super(new VerticalFlowLayout(true, true));
    myGenerator = generator;

    layoutPanel();
    initListeners();
  }

  private void layoutPanel() {
    myCoursesComboBox = new ComboBox<CourseInfo>();

    final JPanel coursesPanel = new JPanel(new BorderLayout());
    final LabeledComponent<ComboBox> coursesCombo = LabeledComponent.create(myCoursesComboBox, "Courses:", BorderLayout.WEST);

    myRefreshButton = new FixedSizeButton(coursesCombo);
    myRefreshButton.setIcon(AllIcons.Actions.Refresh);
    myBrowseButton = new FixedSizeButton(coursesCombo);

    final JPanel comboPanel = new JPanel(new BorderLayout());

    comboPanel.add(coursesCombo, BorderLayout.CENTER);
    comboPanel.add(myRefreshButton, BorderLayout.EAST);

    coursesPanel.add(comboPanel, BorderLayout.CENTER);
    coursesPanel.add(myBrowseButton, BorderLayout.EAST);

    add(coursesPanel);
    myAnchor = coursesCombo;

    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel invisibleLabel = new JLabel();
    invisibleLabel.setPreferredSize(new JLabel("Location: ").getPreferredSize());
    panel.add(invisibleLabel, BorderLayout.WEST);

    myInfoPanel = new JPanel(new VerticalFlowLayout());
    myAuthorLabel = new JLabel();
    myDescriptionPane = new JTextPane();
    myDescriptionPane.setEditable(true);
    myDescriptionPane.setEnabled(true);
    myAuthorLabel.setEnabled(true);
    myDescriptionPane.setPreferredSize(new Dimension(150, 100));
    myInfoPanel.add(myAuthorLabel);
    myInfoPanel.add(myDescriptionPane);
    myInfoPanel.setBorder(BorderFactory.createLineBorder(new JBColor(10067616, 10067616)));

    panel.add(myInfoPanel, BorderLayout.CENTER);
    add(panel);
  }

  private void initCoursesCombobox() {
    myAvailableCourses =
      myGenerator.getCoursesUnderProgress(false, "Getting Available Courses", ProjectManager.getInstance().getDefaultProject());
    if (myAvailableCourses == null || myAvailableCourses.isEmpty()) {
      setError(CONNECTION_ERROR);
    }
    else {
      addCoursesToCombobox(myAvailableCourses);
      final CourseInfo selectedCourse = StudyUtils.getFirst(myAvailableCourses);
      final String authorsString = Course.getAuthorsString(selectedCourse.getAuthors());
      myAuthorLabel.setText(!StringUtil.isEmptyOrSpaces(authorsString) ? "Author: " + authorsString : "");
      myDescriptionPane.setText(selectedCourse.getDescription());
      myDescriptionPane.setEditable(false);
      //setting the first course in list as selected
      myGenerator.setSelectedCourse(selectedCourse);

      if (selectedCourse.isAdaptive() && !myGenerator.isLoggedIn()) {
        setError(LOGIN_TO_STEPIC_MESSAGE);
      }
      else {
        setOK();
      }
    }
  }

  private void setupBrowseButton() {
    myBrowseButton.setIcon(InteractiveLearningIcons.InterpreterGear);
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
      public void actionPerformed(ActionEvent e) {
        final BaseListPopupStep<String> popupStep = new BaseListPopupStep<String>("", "Add local course", LOGIN_TO_STEPIC) {
          @Override
          public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
            return doFinalStep(() -> {
              if ("Add local course".equals(selectedValue)) {

                Project[] projects = ProjectManager.getInstance().getOpenProjects();
                FileChooser.chooseFile(fileChooser, null, projects.length == 0 ? null : projects[0].getBaseDir(),
                                       file -> {
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
                                       });
              }
              else if (LOGIN_TO_STEPIC.equals(selectedValue)) {
                showLoginDialog();
              }
            });
          }
        };
        final ListPopup popup = JBPopupFactory.getInstance().createListPopup(popupStep);
        popup.showInScreenCoordinates(myBrowseButton, myBrowseButton.getLocationOnScreen());
      }
    });
  }

  public void showLoginDialog() {
    final AddRemoteDialog dialog = new AddRemoteDialog();
    dialog.show();
  }

  private void initListeners() {
    myRefreshButton.addActionListener(new RefreshActionListener());
    myCoursesComboBox.addActionListener(new CourseSelectedListener());
    setupBrowseButton();
    addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorMoved(AncestorEvent event) {
        if (!isComboboxInitialized && isVisible()) {
          isComboboxInitialized = true;
          initCoursesCombobox();
        }
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
  public void registerValidators(final FacetValidatorsManager manager) {
    myValidationManager = manager;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    myAnchor = anchor;
  }

  /**
   * Handles refreshing courses
   * Old courses added to new courses only if their
   * meta file still exists in local file system
   */
  private class RefreshActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      final List<CourseInfo> courses =
        myGenerator.getCoursesUnderProgress(true, "Refreshing Course List", DefaultProjectFactory.getInstance().getDefaultProject());
      if (courses != null) {
        refreshCoursesList(courses);
      }
    }
  }

  private void refreshCoursesList(@NotNull final List<CourseInfo> courses) {
    if (courses.isEmpty()) {
      setError(CONNECTION_ERROR);
      return;
    }
    myCoursesComboBox.removeAllItems();

    addCoursesToCombobox(courses);
    myGenerator.setSelectedCourse(StudyUtils.getFirst(courses));

    myGenerator.setCourses(courses);
    myAvailableCourses = courses;
    StudyProjectGenerator.flushCache(myAvailableCourses);
  }

  private void addCoursesToCombobox(@NotNull List<CourseInfo> courses) {
    for (CourseInfo courseInfo : courses) {
      myCoursesComboBox.addItem(courseInfo);
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
        myDescriptionPane.setText("");
        setError(INVALID_COURSE);
        return;
      }
      final String authorsString = Course.getAuthorsString(selectedCourse.getAuthors());
      myAuthorLabel.setText(!StringUtil.isEmptyOrSpaces(authorsString) ? "Author: " + authorsString : "");
      myCoursesComboBox.removeItem(CourseInfo.INVALID_COURSE);
      myDescriptionPane.setText(selectedCourse.getDescription());
      myGenerator.setSelectedCourse(selectedCourse);

      setOK();
      if (selectedCourse.isAdaptive()) {
        if(!myGenerator.isLoggedIn()) {
          setError(LOGIN_TO_STEPIC_MESSAGE);
        }
      }
    }
  }

  public JComboBox<CourseInfo> getCoursesComboBox() {
    return myCoursesComboBox;
  }

  public JPanel getInfoPanel() {
    return myInfoPanel;
  }

  private class AddRemoteDialog extends DialogWrapper {

    private final StudyAddRemoteCourse myRemoteCourse;

    protected AddRemoteDialog() {
      super(null);
      setTitle("Login To Stepic");
      myRemoteCourse = new StudyAddRemoteCourse();
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myRemoteCourse.getContentPanel();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myRemoteCourse.getLoginField();
    }

    @Override
    protected void doOKAction() {
      super.doOKAction();
      if (StringUtil.isEmptyOrSpaces(myRemoteCourse.getLogin())) {
        myRemoteCourse.setError("Please, enter your login");
        return;
      }
      if (StringUtil.isEmptyOrSpaces(myRemoteCourse.getPassword())) {
        myRemoteCourse.setError("Please, enter your password");
        return;
      }

      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);

        final StepicUser stepicUser = StudyUtils.execCancelable(() -> EduStepicConnector.login(myRemoteCourse.getLogin(),
                                                                                               myRemoteCourse.getPassword()));
        if (stepicUser != null) {
          stepicUser.setEmail(myRemoteCourse.getLogin());
          stepicUser.setPassword(myRemoteCourse.getPassword());
          myGenerator.myUser = stepicUser;
          myGenerator.setEnrolledCoursesIds(EduStepicConnector.getEnrolledCoursesIds());

          final List<CourseInfo> courses = myGenerator.getCourses(true);
          if (courses != null) {
            ApplicationManager.getApplication().invokeLater(() -> refreshCoursesList(courses));
          }
        }
        else {
          setError("Failed to login");
        }
      }, "Signing In And Getting Stepic Course List", true, new DefaultProjectFactoryImpl().getDefaultProject());
    }
  }
}
