package com.intellij.tasks.generic;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: Evgeny.Zakrevsky
 * Date: 10/4/12
 */
public class GenericRepositoryType extends BaseRepositoryType<GenericRepository> {
  @NotNull
  @Override
  public String getName() {
    return "Generic";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.General.Web;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new GenericRepository(this);
  }

  @Override
  public Class<GenericRepository> getRepositoryClass() {
    return GenericRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(final GenericRepository repository,
                                           final Project project,
                                           final Consumer<GenericRepository> changeListener) {
    return new GenericRepositoryEditor<GenericRepository>(project, repository, changeListener);
  }
}
