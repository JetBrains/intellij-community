package com.intellij.tasks.pivotal;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dennis.Ushakov
 */
public class PivotalTrackerRepositoryType extends BaseRepositoryType<PivotalTrackerRepository> {
  static final Icon ICON = IconLoader.getIcon("/icons/pivotal.png");

  @NotNull
  @Override
  public String getName() {
    return "PivotalTracker";
  }

  @Override
  public Icon getIcon() {
    return ICON;
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
    return EnumSet.of(TaskState.SUBMITTED, TaskState.OPEN, TaskState.RESOLVED, TaskState.OTHER);
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(PivotalTrackerRepository repository,
                                           Project project,
                                           Consumer<PivotalTrackerRepository> changeListener) {
    return new PivotalTrackerRepositoryEditor(project, repository, changeListener);
  }

  @Override
  protected int getFeatures() {
    return BASIC_HTTP_AUTHORIZATION;
  }
}
