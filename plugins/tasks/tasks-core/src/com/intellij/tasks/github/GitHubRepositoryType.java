package com.intellij.tasks.github;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dennis.Ushakov
 */
public class GitHubRepositoryType extends BaseRepositoryType<GitHubRepository> {
  static final Icon ICON = TasksCoreIcons.Github;

  @NotNull
  @Override
  public String getName() {
    return "GitHub";
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new GitHubRepository(this);
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.OPEN, TaskState.RESOLVED);
  }

  @Override
  public Class<GitHubRepository> getRepositoryClass() {
    return GitHubRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(GitHubRepository repository,
                                           Project project,
                                           Consumer<GitHubRepository> changeListener) {
    return new GitHubRepositoryEditor(project, repository, changeListener);
  }

  @Override
  protected int getFeatures() {
    return BASIC_HTTP_AUTHORIZATION;
  }
}
