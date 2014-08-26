package org.jetbrains.plugins.coursecreator;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.format.Course;
import org.jetbrains.plugins.coursecreator.ui.CCNewProjectPanel;

import javax.swing.*;


public class CCProjectGenerator extends PythonProjectGenerator implements DirectoryProjectGenerator {
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
    return null;
  }


  @Override
  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                              @Nullable Object settings, @NotNull Module module) {

    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = new Course(mySettingsPanel.getName(), mySettingsPanel.getAuthor(), mySettingsPanel.getDescription());
    service.setCourse(course);

    final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(baseDir);
    if (projectDir == null) return;
    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        final FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate("test_helper");
        try {
          FileTemplateUtil.createFromTemplate(template, "test_helper.py", null, projectDir);
        }
        catch (Exception ignored) {
        }
        DirectoryUtil.createSubdirectories("hints", projectDir, "\\/");
      }
    }.execute();

  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    String message = "";
    message = mySettingsPanel.getDescription().equals("") ? "Enter description" : message;
    message = mySettingsPanel.getAuthor().equals("") ? "Enter author name" : message;
    message = mySettingsPanel.getName().equals("") ? "Enter course name" : message;
    return message.equals("")? ValidationResult.OK : new ValidationResult(message) ;
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
