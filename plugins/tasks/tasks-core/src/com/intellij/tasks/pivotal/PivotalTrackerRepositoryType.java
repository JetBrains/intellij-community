package com.intellij.tasks.pivotal;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dennis.Ushakov
 */
public class PivotalTrackerRepositoryType extends BaseRepositoryType<PivotalTrackerRepository> {

  @NotNull
  @Override
  public String getName() {
    return "PivotalTracker";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksIcons.Pivotal;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new PivotalTrackerRepository(this);
  }

  @Override
  public Class<PivotalTrackerRepository> getRepositoryClass() {
    return PivotalTrackerRepository.class;
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.SUBMITTED, TaskState.OPEN, TaskState.RESOLVED, TaskState.OTHER, TaskState.IN_PROGRESS);
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(PivotalTrackerRepository repository,
                                           Project project,
                                           Consumer<PivotalTrackerRepository> changeListener) {
    return new PivotalTrackerRepositoryEditor(project, repository, changeListener);
  }
}
