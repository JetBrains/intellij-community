package com.intellij.tasks.fogbugz;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author mkennedy
 */
public class FogBugzRepositoryType extends BaseRepositoryType<FogBugzRepository> {
  private static final Icon ICON = TasksCoreIcons.FogBugz;

  @NotNull
  @Override
  public String getName() {
    return "FogBugz";
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new FogBugzRepository(this);
  }

  @Override
  public Class<FogBugzRepository> getRepositoryClass() {
    return FogBugzRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(FogBugzRepository repository, Project project, Consumer<FogBugzRepository> changeListener) {
    return new FogBugzRepositoryEditor(project, repository, changeListener);
  }
}
