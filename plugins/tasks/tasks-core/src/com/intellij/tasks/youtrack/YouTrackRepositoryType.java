package com.intellij.tasks.youtrack;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  @Override
  public String getAdvertiser() {
    return "<html>Not YouTrack customer yet? Get <a href='https://www.jetbrains.com/youtrack/download/get_youtrack.html?idea_integration'>YouTrack</a></html>";
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
    return EnumSet.of(TaskState.IN_PROGRESS, TaskState.RESOLVED);
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(YouTrackRepository repository, Project project, Consumer<YouTrackRepository> changeListener) {
    return new YouTrackRepositoryEditor(project, repository, changeListener);
  }
}
