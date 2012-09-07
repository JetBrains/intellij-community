package com.intellij.tasks.mantis;

import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.BaseRepositoryType;
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

}
