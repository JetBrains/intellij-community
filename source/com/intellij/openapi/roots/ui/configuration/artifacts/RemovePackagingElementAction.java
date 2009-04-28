package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.util.Icons;

/**
 * @author nik
 */
public class RemovePackagingElementAction extends AnAction {
  private final ArtifactsEditor myArtifactsEditor;

  public RemovePackagingElementAction(ArtifactsEditor artifactsEditor) {
    super(ProjectBundle.message("action.name.remove.packaging.element"), ProjectBundle.message("action.description.remove.packaging.elements"), Icons.DELETE_ICON);
    myArtifactsEditor = artifactsEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(myArtifactsEditor.getPackagingElementsTree().getSelectedNodes().length > 0
                                   && !myArtifactsEditor.getPackagingElementsTree().isEditing());
  }

  public void actionPerformed(AnActionEvent e) {
    myArtifactsEditor.removeSelectedElements();
  }
}
