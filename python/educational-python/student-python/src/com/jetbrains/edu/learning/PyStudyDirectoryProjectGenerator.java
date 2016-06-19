package com.jetbrains.edu.learning;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.BooleanFunction;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.stepic.CourseInfo;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.ui.StudyNewProjectPanel;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import icons.InteractiveLearningPythonIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;


public class PyStudyDirectoryProjectGenerator extends PythonProjectGenerator implements DirectoryProjectGenerator {
  private static final Logger LOG = Logger.getInstance(PyStudyDirectoryProjectGenerator.class.getName());
  private final StudyProjectGenerator myGenerator;
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
          mySettingsPanel.showLoginDialog();
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
  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                              @Nullable Object settings, @NotNull Module module) {
    myGenerator.generateProject(project, baseDir);
    final FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate("test_helper");
    final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(baseDir);
    if (projectDir == null) return;
    try {
      FileTemplateUtil.createFromTemplate(template, "test_helper.py", null, projectDir);
    }
    catch (Exception exception) {
      LOG.error("Can't copy test_helper.py " + exception.getMessage());
    }
  }
  
  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    return myValidationResult;
  }

  public void setValidationResult(ValidationResult validationResult) {
    myValidationResult = validationResult;
  }

  @Nullable
  @Override
  public JPanel extendBasePanel() throws ProcessCanceledException {
    return mySettingsPanel.getContentPanel();
  }

  public List<CourseInfo> getCourses() {
    return myGenerator.getCoursesUnderProgress(false, "Getting Courses", ProjectManager.getInstance().getDefaultProject());
  }

  public void setSelectedCourse(CourseInfo course) {
    myGenerator.setSelectedCourse(course);
  }

  public StudyProjectGenerator getGenerator() {
    return myGenerator;
  }

  @Nullable
  @Override
  public BooleanFunction<PythonProjectGenerator> beforeProjectGenerated(@NotNull Sdk sdk) {
    return new BooleanFunction<PythonProjectGenerator>() {
      @Override
      public boolean fun(PythonProjectGenerator generator) {
        final List<Integer> enrolledCoursesIds = myGenerator.getEnrolledCoursesIds();
        final CourseInfo course = (CourseInfo)mySettingsPanel.getCoursesComboBox().getSelectedItem();
        if (course.isAdaptive() && !enrolledCoursesIds.contains(course.getId())) {
          ProgressManager.getInstance().runProcessWithProgressSynchronously(new ThrowableComputable<Boolean, RuntimeException>() {
            @Override
            public Boolean compute() throws RuntimeException {
              ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
              return StudyUtils.execCancelable(() -> EduStepicConnector.enrollToCourse(course.getId()));
            }
          }, "Creating Course", true, ProjectManager.getInstance().getDefaultProject());
          
        }
        return true;
      }
    };
  }
}
