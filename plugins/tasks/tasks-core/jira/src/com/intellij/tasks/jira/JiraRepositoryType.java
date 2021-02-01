package com.intellij.tasks.jira;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dmitry Avdeev
 */
public class JiraRepositoryType extends BaseRepositoryType<JiraRepository> {

  public JiraRepositoryType() {
  }

  @Override
  @NotNull
  public String getName() {
    return "JIRA";
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return TasksCoreIcons.Jira;
  }

  @Override
  @NotNull
  public JiraRepository createRepository() {
    return new JiraRepository(this);
  }

  @NotNull
  @Override
  public Class<JiraRepository> getRepositoryClass() {
    return JiraRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(JiraRepository repository,
                                           Project project,
                                           Consumer<? super JiraRepository> changeListener) {
    return new JiraRepositoryEditor(project, repository, changeListener);
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.OPEN, TaskState.IN_PROGRESS, TaskState.REOPENED, TaskState.RESOLVED);
  }
}

