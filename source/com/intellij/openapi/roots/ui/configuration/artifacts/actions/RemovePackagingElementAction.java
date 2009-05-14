package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditor;
import com.intellij.util.Icons;

/**
 * @author nik
 */
public class RemovePackagingElementAction extends AnAction {
  private final ArtifactEditor myArtifactEditor;

  public RemovePackagingElementAction(ArtifactEditor artifactEditor) {
    super(ProjectBundle.message("action.name.remove.packaging.element"), ProjectBundle.message("action.description.remove.packaging.elements"), Icons.DELETE_ICON);
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(!myArtifactEditor.getPackagingElementsTree().getSelection().getElements().isEmpty()
                                   && !myArtifactEditor.getPackagingElementsTree().isEditing());
  }

  public void actionPerformed(AnActionEvent e) {
    myArtifactEditor.removeSelectedElements();
  }
}
