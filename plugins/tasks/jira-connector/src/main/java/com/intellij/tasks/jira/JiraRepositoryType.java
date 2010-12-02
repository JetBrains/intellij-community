package com.intellij.tasks.jira;

import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.impl.BaseRepositoryType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class JiraRepositoryType extends BaseRepositoryType<JiraRepository> {

  public JiraRepositoryType() {
  }

  @NotNull
  public String getName() {
    return "JIRA";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/resources/jira-blue-16.png");
  }

  @NotNull
  public JiraRepository createRepository() {
    return new JiraRepository(this);
  }

  @NotNull
  @Override
  public Class<JiraRepository> getRepositoryClass() {
    return JiraRepository.class;
  }

  @Override
  protected int getFeatures() {
    return BASIC_HTTP_AUTHORIZATION;
  }
  //
  //@Override
  //public EnumSet<TaskState> getPossibleTaskStates() {
  //  return EnumSet.of(TaskState.OPEN, TaskState.IN_PROGRESS, TaskState.REOPENED, TaskState.RESOLVED);
  //}
}

