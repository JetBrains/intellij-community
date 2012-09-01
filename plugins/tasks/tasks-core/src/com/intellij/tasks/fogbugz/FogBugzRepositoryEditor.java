package com.intellij.tasks.fogbugz;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.util.Consumer;

/**
 * @author mkennedy
 */
public class FogBugzRepositoryEditor extends BaseRepositoryEditor<FogBugzRepository> {

  public FogBugzRepositoryEditor(Project project, FogBugzRepository repository, Consumer<FogBugzRepository> changeListener) {
    super(project, repository, changeListener);
  }
}
