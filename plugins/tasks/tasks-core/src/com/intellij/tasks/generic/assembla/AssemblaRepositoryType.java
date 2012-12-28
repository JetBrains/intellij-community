package com.intellij.tasks.generic.assembla;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.util.Consumer;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: evgeny.zakrevsky
 * Date: 10/26/12
 */
public class AssemblaRepositoryType extends TaskRepositoryType<AssemblaRepository> {
  @NotNull
  @Override
  public String getName() {
    return "Assembla";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksIcons.Assembla;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(final AssemblaRepository repository,
                                           final Project project,
                                           final Consumer<AssemblaRepository> changeListener) {
    return new AssemblaRepositoryEditor(project, repository, changeListener);
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new AssemblaRepository(this);
  }

  @Override
  public Class<AssemblaRepository> getRepositoryClass() {
    return AssemblaRepository.class;
  }
}
