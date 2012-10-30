package com.intellij.tasks.generic.assembla;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.generic.GenericRepositoryEditor;
import com.intellij.util.Consumer;

/**
 * User: evgeny.zakrevsky
 * Date: 10/27/12
 */
public class AssemblaRepositoryEditor extends GenericRepositoryEditor<AssemblaRepository> {
  public AssemblaRepositoryEditor(final Project project,
                                  final AssemblaRepository repository,
                                  final Consumer<AssemblaRepository> changeListener) {
    super(project, repository, changeListener);
    myShareUrlCheckBox.setVisible(false);
    myUrlLabel.setVisible(false);
    myURLText.setVisible(false);
  }
}
