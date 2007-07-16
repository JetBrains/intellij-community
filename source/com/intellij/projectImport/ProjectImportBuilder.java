package com.intellij.projectImport;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class ProjectImportBuilder extends ProjectBuilder {
  public static final ExtensionPointName<ProjectImportBuilder> EXTENSIONS_POINT_NAME = ExtensionPointName.create("com.intellij.projectImportBuilder");

  private boolean myUpdate;

  public abstract String getName();

  public abstract Icon getIcon();

  protected String getTitle() {
    return IdeBundle.message("project.import.wizard.title", getName());
  }

  public boolean isUpdate() {
    return myUpdate;
  }

  public void setUpdate(final boolean update) {
    myUpdate = update;
  }

  @Nullable
  public static Project getCurrentProject() {
    return (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);
  }
}
