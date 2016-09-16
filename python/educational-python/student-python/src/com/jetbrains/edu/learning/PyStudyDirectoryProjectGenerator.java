package com.jetbrains.edu.learning;

import com.intellij.execution.ExecutionException;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.BooleanFunction;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.stepic.CourseInfo;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.ui.StudyNewProjectPanel;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.AbstractCreateVirtualEnvDialog;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import icons.InteractiveLearningPythonIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;


public class PyStudyDirectoryProjectGenerator extends PythonProjectGenerator<PyNewProjectSettings>  {
  private static final Logger LOG = Logger.getInstance(PyStudyDirectoryProjectGenerator.class.getName());
  private final StudyProjectGenerator myGenerator;
  private static final String NO_PYTHON_INTERPRETER = "<html><u>Add</u> python interpreter.</html>";
  public ValidationResult myValidationResult = new ValidationResult("selected course is not valid");
  private StudyNewProjectPanel mySettingsPanel;

  public PyStudyDirectoryProjectGenerator() {
    myGenerator = new StudyProjectGenerator();
    myGenerator.addSettingsStateListener(new StudyProjectGenerator.SettingsListener() {
      @Override
      public void stateChanged(ValidationResult result) {
        setValidationResult(result);
      }
    });

    mySettingsPanel = new StudyNewProjectPanel(myGenerator);
    mySettingsPanel.registerValidators(new FacetValidatorsManager() {
      public void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch) {
        throw new UnsupportedOperationException();
      }

      public void validate() {
        ApplicationManager.getApplication().invokeLater(() -> fireStateChanged());
      }
    });

    addErrorLabelMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (((CourseInfo)mySettingsPanel.getCoursesComboBox().getSelectedItem()).isAdaptive() && !myGenerator.isLoggedIn()) {
          mySettingsPanel.showLoginDialog(false, "Signing In");
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (((CourseInfo)mySettingsPanel.getCoursesComboBox().getSelectedItem()).isAdaptive() && !myGenerator.isLoggedIn()) {
          e.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (((CourseInfo)mySettingsPanel.getCoursesComboBox().getSelectedItem()).isAdaptive() && !myGenerator.isLoggedIn()) {
          e.getComponent().setCursor(Cursor.getDefaultCursor());
        }
      }
    });
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Educational";
  }

  @Nullable
  @Override
  public Icon getLogo() {
    return InteractiveLearningPythonIcons.EducationalProjectType;
  }


  @Override
  public void configureProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                               @NotNull PyNewProjectSettings settings, @NotNull Module module) {
    myGenerator.generateProject(project, baseDir);
    final String testHelper = "test_helper.py";
    if (baseDir.findChild(testHelper) != null) return;
    final FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate("test_helper");
    final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(baseDir);
    if (projectDir == null) return;
    try {
      FileTemplateUtil.createFromTemplate(template, testHelper, null, projectDir);
    }
    catch (Exception exception) {
      LOG.error("Can't copy test_helper.py " + exception.getMessage());
    }
  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    final Project project = ProjectManager.getInstance().getDefaultProject();
    final List<Sdk> sdks = PyConfigurableInterpreterList.getInstance(project).getAllPythonSdks();
    if (sdks.isEmpty()) {
      myValidationResult = new ValidationResult(NO_PYTHON_INTERPRETER);
    }

    return myValidationResult;
  }

  public void setValidationResult(ValidationResult validationResult) {
    myValidationResult = validationResult;
  }

  @Nullable
  @Override
  public JPanel extendBasePanel() throws ProcessCanceledException {
    return mySettingsPanel;
  }

  @NotNull
  public List<CourseInfo> getCourses() {
    return myGenerator.getCoursesUnderProgress(false, "Getting Courses", ProjectManager.getInstance().getDefaultProject());
  }

  public void setSelectedCourse(CourseInfo course) {
    myGenerator.setSelectedCourse(course);
  }

  public StudyProjectGenerator getGenerator() {
    return myGenerator;
  }

  @Override
  public boolean hideInterpreter() {
    return true;
  }

  @Nullable
  @Override
  public BooleanFunction<PythonProjectGenerator> beforeProjectGenerated(@Nullable Sdk sdk) {
    return generator -> {
      final List<Integer> enrolledCoursesIds = myGenerator.getEnrolledCoursesIds();
      final CourseInfo course = (CourseInfo)mySettingsPanel.getCoursesComboBox().getSelectedItem();
      if (course == null) return true;
      if (course.isAdaptive() && !enrolledCoursesIds.contains(course.getId())) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
          return StudyUtils.execCancelable(() -> EduStepicConnector.enrollToCourse(course.getId(), myGenerator.myUser));
        }, "Creating Course", true, ProjectManager.getInstance().getDefaultProject());

      }
      return true;
    };
  }

  public void createAndAddVirtualEnv(Project project, PyNewProjectSettings settings) {
    final ProjectSdksModel model = PyConfigurableInterpreterList.getInstance(project).getModel();
    final String baseSdk = getBaseSdk(project);

    if (baseSdk != null) {
      final PyPackageManager packageManager = PyPackageManager.getInstance(new PyDetectedSdk(baseSdk));
      try {
        final String path = packageManager.createVirtualEnv(project.getBasePath() + "/.idea/VirtualEnvironment", false);
        AbstractCreateVirtualEnvDialog.setupVirtualEnvSdk(path, true, new AbstractCreateVirtualEnvDialog.VirtualEnvCallback() {
          @Override
          public void virtualEnvCreated(Sdk createdSdk, boolean associateWithProject) {
            settings.setSdk(createdSdk);
            model.addSdk(createdSdk);
            try {
              model.apply();
            }
            catch (ConfigurationException exception) {
              LOG.error("Error adding created virtual env " + exception.getMessage());
            }
            if (associateWithProject) {
              SdkAdditionalData additionalData = createdSdk.getSdkAdditionalData();
              if (additionalData == null) {
                additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(createdSdk.getHomePath()));
                ((ProjectJdkImpl)createdSdk).setSdkAdditionalData(additionalData);
              }
              ((PythonSdkAdditionalData)additionalData).associateWithNewProject();
            }
          }
        });
      }
      catch (ExecutionException e) {
        LOG.warn("Failed to create virtual env " + e.getMessage());
      }
    }
  }

  private static String getBaseSdk(@NotNull final Project project) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    LanguageLevel baseLevel = LanguageLevel.PYTHON30;
    if (course != null) {
      final String version = course.getLanguageVersion();
      if (PyStudyLanguageManager.PYTHON_2.equals(version)) {
        baseLevel = LanguageLevel.PYTHON27;
      }
      else if (PyStudyLanguageManager.PYTHON_3.equals(version)) {
        baseLevel = LanguageLevel.PYTHON31;
      }
      else if (version != null) {
        baseLevel = LanguageLevel.fromPythonVersion(version);
      }
    }
    final PythonSdkFlavor flavor = PythonSdkFlavor.getApplicableFlavors(false).get(0);
    String baseSdk = null;
    final Collection<String> baseSdks = flavor.suggestHomePaths();
    for (String sdk : baseSdks) {
      final String versionString = flavor.getVersionString(sdk);
      final String prefix = flavor.getName() + " ";
      if (versionString != null && versionString.startsWith(prefix)) {
        final LanguageLevel level = LanguageLevel.fromPythonVersion(versionString.substring(prefix.length()));
        if (level.isAtLeast(baseLevel)) {
          baseSdk = sdk;
          break;
        }
      }
    }
    return baseSdk != null ? baseSdk : baseSdks.iterator().next();
  }
}
