package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@State(
  name = "ProjectLibrariesConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class ProjectLibrariesConfigurable extends BaseLibrariesConfigurable {

  public ProjectLibrariesConfigurable(final Project project) {
    super(project);
    myLevel = LibraryTablesRegistrar.PROJECT_LEVEL;
  }

  @Nls
  public String getDisplayName() {
    return "Libraries";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  @NonNls
  public String getId() {
    return "project.libraries";
  }


  protected LibraryTableModifiableModelProvider getModelProvider(final StructureConfigrableContext context, final boolean editable) {
    return context.getProjectLibrariesProvider(editable);
  }

  public static ProjectLibrariesConfigurable getInstance(final Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, ProjectLibrariesConfigurable.class);
  }

  protected String getAddText() {
    return ProjectBundle.message("add.new.project.library.text");
  }
}
