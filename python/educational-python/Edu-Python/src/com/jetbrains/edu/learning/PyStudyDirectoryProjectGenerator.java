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
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.BooleanFunction;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.ui.StudyNewProjectPanel;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.remote.PyProjectSynchronizer;
import com.jetbrains.python.sdk.AbstractCreateVirtualEnvDialog;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
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

public class PyStudyDirectoryProjectGenerator extends PythonProjectGenerator<PyNewProjectSettings>
  implements EduCourseProjectGenerator {
  private static final Logger LOG = Logger.getInstance(PyStudyDirectoryProjectGenerator.class.getName());
  private final StudyProjectGenerator myGenerator;
  private static final String NO_PYTHON_INTERPRETER = "<html><u>Add</u> python interpreter.</html>";
  private final boolean isLocal;
  public ValidationResult myValidationResult = new ValidationResult("selected course is not valid");

  @SuppressWarnings("unused") // used on startup
  public PyStudyDirectoryProjectGenerator() {
    this(false);
  }

  public PyStudyDirectoryProjectGenerator(boolean isLocal) {
    this.isLocal = isLocal;
    myGenerator = new StudyProjectGenerator();
    myGenerator.addSettingsStateListener(new StudyProjectGenerator.SettingsListener() {
      @Override
      public void stateChanged(ValidationResult result) {
        setValidationResult(result);
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
                               @NotNull PyNewProjectSettings settings,
                               @NotNull Module module,
                               @Nullable PyProjectSynchronizer synchronizer) {
    myGenerator.generateProject(project, baseDir);
    createTestHelper(project, baseDir);
  }

  private static void createTestHelper(@NotNull Project project, @NotNull VirtualFile baseDir) {
    final String testHelper = EduNames.TEST_HELPER;
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
  public DirectoryProjectGenerator getDirectoryProjectGenerator() {
    return this;
  }

  public ValidationResult validate() {
    final Project project = ProjectManager.getInstance().getDefaultProject();
    final List<Sdk> sdks = PyConfigurableInterpreterList.getInstance(project).getAllPythonSdks();

    ValidationResult validationResult;
    if (sdks.isEmpty()) {
      validationResult = new ValidationResult(NO_PYTHON_INTERPRETER);
    } else {
      validationResult = ValidationResult.OK;
    }

    return validationResult;
  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    ValidationResult validationResult = validate();
    if (!validationResult.isOk()) {
      myValidationResult = validationResult;
    }

    return myValidationResult;
  }

  @Override
  public boolean beforeProjectGenerated() {
    BooleanFunction<PythonProjectGenerator> function = beforeProjectGenerated(null);
    return function != null && function.fun(this);
  }

  @Override
  public void afterProjectGenerated(@NotNull Project project) {
    PyNewProjectSettings settings = (PyNewProjectSettings)getProjectSettings();
    Sdk sdk = settings.getSdk();

    if (sdk == null) {
      createAndAddVirtualEnv(project, settings);
      sdk = settings.getSdk();
    }

    SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk);
    final List<Sdk> sdks = PythonSdkType.getAllSdks();
    for (Sdk s : sdks) {
      final SdkAdditionalData additionalData = s.getSdkAdditionalData();
      if (additionalData instanceof PythonSdkAdditionalData) {
        ((PythonSdkAdditionalData)additionalData).reassociateWithCreatedProject(project);
      }
    }
  }

  public void setValidationResult(ValidationResult validationResult) {
    myValidationResult = validationResult;
  }

  @Nullable
  @Override
  public JPanel extendBasePanel() throws ProcessCanceledException {
    StudyNewProjectPanel mySettingsPanel = new StudyNewProjectPanel(myGenerator, isLocal);
    mySettingsPanel.registerValidators(new FacetValidatorsManager() {
      public void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch) {
        throw new UnsupportedOperationException();
      }

      public void validate() {
        ApplicationManager.getApplication().invokeLater(() -> fireStateChanged());
      }
    });

    addErrorLabelMouseListener(new MouseAdapter() {
      private boolean isCourseAdaptiveAndNotLogged() {
        Course course = myGenerator.getSelectedCourse();
        return course != null && course.isAdaptive() && !myGenerator.isLoggedIn();
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (isCourseAdaptiveAndNotLogged()) {
          mySettingsPanel.showLoginDialog(false, "Signing In");
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (isCourseAdaptiveAndNotLogged()) {
          e.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (isCourseAdaptiveAndNotLogged()) {
          e.getComponent().setCursor(Cursor.getDefaultCursor());
        }
      }
    });

    return mySettingsPanel;
  }

  public void setCourse(@NotNull Course course) {
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
      final Course course = myGenerator.getSelectedCourse();
      if (course == null || !(course instanceof RemoteCourse)) return true;
      if (((RemoteCourse)course).getId() > 0 && !enrolledCoursesIds.contains(((RemoteCourse)course).getId())) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
          return StudyUtils.execCancelable(() -> EduStepicConnector.enrollToCourse(((RemoteCourse)course).getId(),
                                                                                   StudySettings.getInstance().getUser()));
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
      if (PyEduPluginConfigurator.PYTHON_2.equals(version)) {
        baseLevel = LanguageLevel.PYTHON27;
      }
      else if (PyEduPluginConfigurator.PYTHON_3.equals(version)) {
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
