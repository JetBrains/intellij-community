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
public class GenericWebRepositoryType extends BaseRepositoryType<GenericWebRepository> {
  @NotNull
  @Override
  public String getName() {
    return "Generic";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.General.Web;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new GenericWebRepository(this);
  }

  @Override
  public Class<GenericWebRepository> getRepositoryClass() {
    return GenericWebRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(final GenericWebRepository repository,
                                           final Project project,
                                           final Consumer<GenericWebRepository> changeListener) {
    return new GenericWebRepositoryEditor(project, repository, changeListener);
  }

  @Override
  protected int getFeatures() {
    return LOGIN_ANONYMOUSLY | BASIC_HTTP_AUTHORIZATION;
  }
}
