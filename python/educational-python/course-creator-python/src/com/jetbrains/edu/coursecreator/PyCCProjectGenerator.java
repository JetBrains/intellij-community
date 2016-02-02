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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.coursecreator.actions.CCCreateLesson;
import com.jetbrains.edu.coursecreator.actions.CCCreateTask;
import com.jetbrains.edu.coursecreator.ui.CCNewProjectPanel;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import icons.CourseCreatorPythonIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class PyCCProjectGenerator extends PythonProjectGenerator implements DirectoryProjectGenerator {
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
  public Object showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
    return null;
  }

  @Nullable
  @Override
  public Icon getLogo() {
    return CourseCreatorPythonIcons.CourseCreationProjectType;
  }

  @Override
  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                              @Nullable Object settings, @NotNull Module module) {
    generateProject(project, baseDir, mySettingsPanel.getName(),
                    mySettingsPanel.getAuthors(), mySettingsPanel.getDescription());
  }

  public static void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                                     @NotNull final String name, @NotNull final String[] authors,
                                     @NotNull final String description) {
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = new Course();
    course.setName(name);
    course.setAuthors(authors);
    course.setDescription(description);
    course.setLanguage("Python");
    service.setCourse(course);

    final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(baseDir);
    if (projectDir == null) return;
    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        final FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate("test_helper");
        try {
          FileTemplateUtil.createFromTemplate(template, "test_helper.py", null, projectDir);
        }
        catch (Exception ignored) {
        }
        PsiDirectory lessonDir = new CCCreateLesson().createItem(null, project, projectDir, course);
        if (lessonDir == null) {
          LOG.error("Failed to create lesson");
          return;
        }
        new CCCreateTask().createItem(null, project, lessonDir, course);
      }
    }.execute();
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
}
