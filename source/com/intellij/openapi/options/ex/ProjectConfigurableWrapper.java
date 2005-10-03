package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.File;

/**
 * @author max
 */
public class ProjectConfigurableWrapper implements Configurable {
  private Project myProject;
  private Configurable myDelegate;

  public ProjectConfigurableWrapper(Project project, Configurable delegate) {
    myProject = project;
    myDelegate = delegate;
  }

  public String getDisplayName() {
    return myDelegate.getDisplayName();
  }

  public void reset() {
    myDelegate.reset();
  }

  public void apply() throws ConfigurationException {
    checkProjectFileWritable();
    myDelegate.apply();
  }

  private void checkProjectFileWritable() {
    final VirtualFile projectFile = myProject.getProjectFile();
    if (projectFile != null) {
      String path = projectFile.getPresentableUrl();
      if (path != null) {
        File file = new File(path);
        if (file.exists() && !file.canWrite()) {
          Messages.showMessageDialog(
            myProject,
            OptionsBundle.message("project.file.read.only.error.message"),
            OptionsBundle.message("cannot.save.settings.default.dialog.title"),
            Messages.getErrorIcon()
          );
        }
      }
    }
  }

  public String getHelpTopic() {
    return myDelegate.getHelpTopic();
  }

  public void disposeUIResources() {
    myDelegate.disposeUIResources();
  }

  public boolean isModified() {
    return myDelegate.isModified();
  }

  public JComponent createComponent() {
    return myDelegate.createComponent();
  }

  public Icon getIcon() {
    return myDelegate.getIcon();
  }
}
