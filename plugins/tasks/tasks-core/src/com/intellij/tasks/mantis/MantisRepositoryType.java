package com.intellij.tasks.mantis;

import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.BaseRepositoryType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class MantisRepositoryType extends BaseRepositoryType<MantisRepository> {
  
  private static final Icon ICON = IconLoader.getIcon("/icons/mantis.png");

  @NotNull
  @Override
  public String getName() {
    return "Mantis";
  }

  @Override
  public Icon getIcon() {
    return ICON;
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
