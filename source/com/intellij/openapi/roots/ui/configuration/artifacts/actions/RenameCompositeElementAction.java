package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditor;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;

import javax.swing.tree.TreePath;

/**
 * @author nik
 */
public class RenameCompositeElementAction extends AnAction {
  private final ArtifactEditor myArtifactEditor;

  public RenameCompositeElementAction(ArtifactEditor artifactEditor) {
    super(ProjectBundle.message("action.name.rename.packaging.element"));
    registerCustomShortcutSet(CommonShortcuts.getRename(), artifactEditor.getPackagingElementsTree().getTreePanel());
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getPackagingElementsTree().getSelection();
    final PackagingElement<?> element = selection.getElementIfSingle();
    e.getPresentation().setVisible(element instanceof CompositePackagingElement && ((CompositePackagingElement)element).canBeRenamed());
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getPackagingElementsTree().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node == null) return;
    final TreePath path = selection.getPath(node);
    myArtifactEditor.getPackagingElementsTree().rename(path);
  }
}
