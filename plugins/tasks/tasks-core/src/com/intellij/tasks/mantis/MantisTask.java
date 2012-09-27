package com.intellij.tasks.mantis;

import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.LocalTaskImpl;
import org.jetbrains.annotations.Nullable;

public class MantisTask extends LocalTaskImpl {
  private MantisProject myProject;
  private MantisRepository myRepository;

  public MantisTask(final String id, final String summary, MantisProject project, MantisRepository repository) {
    super(id, summary);
    myProject = project;
    myRepository = repository;
  }

  @Override
  public String getIssueUrl() {
    return myRepository.getUrl() + "/view.php?id=" + getId();
  }

  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }

  @Nullable
  @Override
  public String getProject() {
    return !MantisProject.ALL_PROJECTS.equals(myProject) ? myProject.getName() : null;
  }
}
