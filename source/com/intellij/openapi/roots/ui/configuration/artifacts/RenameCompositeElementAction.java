package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.packaging.elements.CompositePackagingElement;

/**
 * @author nik
 */
public class RenameCompositeElementAction extends AnAction {
  private final ArtifactsEditor myArtifactsEditor;

  public RenameCompositeElementAction(ArtifactsEditor artifactsEditor) {
    super("Rename");
    registerCustomShortcutSet(CommonShortcuts.getRename(), artifactsEditor.getPackagingElementsTree().getTreePanel());
    myArtifactsEditor = artifactsEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final PackagingElementNode[] nodes = myArtifactsEditor.getPackagingElementsTree().getSelectedNodes();
    e.getPresentation().setVisible(nodes.length == 1 && nodes[0].getPackagingElement() instanceof CompositePackagingElement);
  }

  public void actionPerformed(AnActionEvent e) {
    final PackagingElementNode[] nodes = myArtifactsEditor.getPackagingElementsTree().getSelectedNodes();
    if (nodes.length != 1) return;

    myArtifactsEditor.getPackagingElementsTree().rename(nodes[0]);
  }
}
