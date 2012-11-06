package com.intellij.tasks.jira;

import com.intellij.tasks.impl.BaseRepositoryType;
import icons.JiraConnectorIcons;
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

  @NotNull
  public Icon getIcon() {
    return JiraConnectorIcons.Jira_blue_16;
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

