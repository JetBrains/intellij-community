package com.intellij.tasks.youtrack;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dmitry Avdeev
 */
public class YouTrackRepositoryType extends BaseRepositoryType<YouTrackRepository> {

  @NotNull
  public String getName() {
    return "YouTrack";
  }

  @NotNull
  public Icon getIcon() {
    return TasksIcons.Youtrack;
  }

  @NotNull
  public YouTrackRepository createRepository() {
    return new YouTrackRepository(this);
  }

  @NotNull
  @Override
  public Class<YouTrackRepository> getRepositoryClass() {
    return YouTrackRepository.class;
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.SUBMITTED, TaskState.OPEN, TaskState.IN_PROGRESS, TaskState.REOPENED, TaskState.RESOLVED);
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(YouTrackRepository repository, Project project, Consumer<YouTrackRepository> changeListener) {
    return new YouTrackRepositoryEditor(project, repository, changeListener);
  }
}
