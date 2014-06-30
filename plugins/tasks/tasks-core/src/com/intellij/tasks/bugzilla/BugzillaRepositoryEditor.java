package com.intellij.tasks.bugzilla;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.util.Consumer;

/**
 * @author Mikhail Golubev
 */
public class BugzillaRepositoryEditor extends BaseRepositoryEditor<BugzillaRepository> {
  public BugzillaRepositoryEditor(Project project,
                                  BugzillaRepository repository,
                                  Consumer<BugzillaRepository> changeListener) {
    super(project, repository, changeListener);

    myUseHttpAuthenticationCheckBox.setVisible(false);
  }
}
