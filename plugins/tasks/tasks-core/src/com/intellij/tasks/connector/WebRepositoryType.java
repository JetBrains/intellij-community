package com.intellij.tasks.connector;

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
public class WebRepositoryType extends BaseRepositoryType<WebRepository> {
  @NotNull
  @Override
  public String getName() {
    return "Web Repository";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.General.Web;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new WebRepository(this);
  }

  @Override
  public Class<WebRepository> getRepositoryClass() {
    return WebRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(final WebRepository repository,
                                           final Project project,
                                           final Consumer<WebRepository> changeListener) {
    return new WebRepositoryEditor(project, repository, changeListener);
  }
}
