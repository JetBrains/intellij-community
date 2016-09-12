package com.jetbrains.edu.coursecreator;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.edu.coursecreator.actions.CCCreateLesson;
import com.jetbrains.edu.coursecreator.actions.CCCreateTask;
import com.jetbrains.edu.coursecreator.ui.CCNewProjectPanel;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import icons.CourseCreatorPythonIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

import static com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator.OUR_COURSES_DIR;


public class PyCCProjectGenerator extends PythonProjectGenerator<PyNewProjectSettings>  {
  private static final Logger LOG = Logger.getInstance(PyCCProjectGenerator.class);
  private CCNewProjectPanel mySettingsPanel;

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Course creation";
  }

  @Nullable
  @Override
  public Icon getLogo() {
    return CourseCreatorPythonIcons.CourseCreationProjectType;
  }

  @Override
  public void configureProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                               @NotNull PyNewProjectSettings settings, @NotNull Module module) {
    generateProject(project, baseDir, mySettingsPanel);
  }

  public static void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir, CCNewProjectPanel settingsPanel) {
    final Course course = getCourse(project, settingsPanel);
    EduUsagesCollector.projectTypeCreated(CCUtils.COURSE_MODE);

    final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(baseDir);
    if (projectDir == null) return;
    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {

        createTestHelper(project, projectDir);

        PsiDirectory lessonDir = new CCCreateLesson().createItem(null, project, projectDir, course);
        if (lessonDir == null) {
          LOG.error("Failed to create lesson");
          return;
        }
        new CCCreateTask().createItem(null, project, lessonDir, course);
      }
    }.execute();
  }

  private static void createTestHelper(@NotNull Project project, PsiDirectory projectDir) {
    final FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate(FileUtil.getNameWithoutExtension(EduNames.TEST_HELPER));
    try {
      FileTemplateUtil.createFromTemplate(template, EduNames.TEST_HELPER, null, projectDir);
    }
    catch (Exception ignored) {
    }
  }

  @NotNull
  private static Course getCourse(@NotNull Project project, @NotNull CCNewProjectPanel settingsPanel) {
    final Course course = new Course();
    String name = settingsPanel.getName();
    course.setName(name);
    course.setAuthorsAsString(settingsPanel.getAuthors());
    course.setDescription(settingsPanel.getDescription());

    String language = PythonLanguage.getInstance().getID();
    course.setLanguage(language);
    course.setCourseMode(CCUtils.COURSE_MODE);

    File courseDir = new File(OUR_COURSES_DIR, name + "-" + project.getName());
    course.setCourseDirectory(courseDir.getPath());

    StudyTaskManager.getInstance(project).setCourse(course);
    StudyUtils.registerStudyToolWindow(course, project);
    return course;
  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    String message = "";
    message = mySettingsPanel.getDescription().isEmpty() ? "Enter description" : message;
    message = mySettingsPanel.getAuthors().length == 0 ? "Enter author name" : message;
    message = mySettingsPanel.getName().isEmpty() ? "Enter course name" : message;
    return message.isEmpty() ? ValidationResult.OK : new ValidationResult(message);
  }

  @Nullable
  @Override
  public JPanel extendBasePanel() throws ProcessCanceledException {
    mySettingsPanel = new CCNewProjectPanel();
    mySettingsPanel.registerValidators(new FacetValidatorsManager() {
      public void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch) {
        throw new UnsupportedOperationException();
      }

      public void validate() {
        fireStateChanged();
      }
    });
    return mySettingsPanel.getMainPanel();
  }

  @Override
  public void locationChanged(@NotNull String newLocation) {
    mySettingsPanel.getNameField().setText(newLocation);
  }
}
