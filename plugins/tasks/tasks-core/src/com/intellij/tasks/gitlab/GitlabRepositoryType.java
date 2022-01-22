package com.intellij.tasks.gitlab;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class GitlabRepositoryType extends BaseRepositoryType<GitlabRepository>{
  @NotNull
  @Override
  public String getName() {
    return "GitLab";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksCoreIcons.Gitlab;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new GitlabRepository(this);
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(GitlabRepository repository,
                                           Project project,
                                           Consumer<? super GitlabRepository> changeListener) {
    return new GitlabRepositoryEditor(project, repository, changeListener);
  }

  @Override
  public Class<GitlabRepository> getRepositoryClass() {
    return GitlabRepository.class;
  }
}
