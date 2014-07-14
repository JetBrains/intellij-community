package com.intellij.tasks.bugzilla;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.util.Consumer;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Mikhail Golubev
 */
public class BugzillaRepositoryType extends TaskRepositoryType<BugzillaRepository> {
  @NotNull
  @Override
  public String getName() {
    return "Bugzilla";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksIcons.Bugzilla;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(BugzillaRepository repository, Project project, Consumer<BugzillaRepository> changeListener) {
    return new BugzillaRepositoryEditor(project, repository, changeListener);
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new BugzillaRepository(this);
  }

  @Override
  public Class<BugzillaRepository> getRepositoryClass() {
    return BugzillaRepository.class;
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    // UNCONFIRMED, CONFIRMED, IN_PROGRESS, RESOLVED (resolution=FIXED)
    return EnumSet.of(TaskState.SUBMITTED, TaskState.OPEN, TaskState.IN_PROGRESS, TaskState.RESOLVED);
  }
}

