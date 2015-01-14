package com.jetbrains.edu.coursecreator;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.jetbrains.edu.coursecreator.ui.CCNewProjectPanel;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import icons.CourseCreatorPythonIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class PyCCProjectGenerator extends PythonProjectGenerator implements DirectoryProjectGenerator {
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
    CCProjectGeneratorUtil.generateProject(project, baseDir, mySettingsPanel.getName(),
                                           mySettingsPanel.getAuthor(), mySettingsPanel.getDescription());
  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    String message = "";
    message = mySettingsPanel.getDescription().isEmpty() ? "Enter description" : message;
    message = mySettingsPanel.getAuthor().isEmpty() ? "Enter author name" : message;
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
