package com.intellij.tasks.mantis;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class MantisRepositoryType extends BaseRepositoryType<MantisRepository> {

  @NotNull
  @Override
  public String getName() {
    return "Mantis";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksIcons.Mantis;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new MantisRepository(this);
  }

  @NotNull
  @Override
  public Class<MantisRepository> getRepositoryClass() {
    return MantisRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(final MantisRepository repository,
                                           final Project project,
                                           final Consumer<MantisRepository> changeListener) {
    return new MantisRepositoryEditor(project, repository, changeListener);
  }
}
