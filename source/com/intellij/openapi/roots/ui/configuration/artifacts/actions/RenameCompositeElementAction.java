package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditor;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;

import javax.swing.tree.TreePath;

/**
 * @author nik
 */
public class RenameCompositeElementAction extends AnAction {
  private final ArtifactsEditor myArtifactsEditor;

  public RenameCompositeElementAction(ArtifactsEditor artifactsEditor) {
    super(ProjectBundle.message("action.name.rename.packaging.element"));
    registerCustomShortcutSet(CommonShortcuts.getRename(), artifactsEditor.getPackagingElementsTree().getTreePanel());
    myArtifactsEditor = artifactsEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactsEditor.getPackagingElementsTree().getSelection();
    final PackagingElement<?> element = selection.getElementIfSingle();
    e.getPresentation().setVisible(element instanceof CompositePackagingElement && ((CompositePackagingElement)element).canBeRenamed());
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactsEditor.getPackagingElementsTree().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node == null) return;
    final TreePath path = selection.getPath(node);
    myArtifactsEditor.getPackagingElementsTree().rename(path);
  }
}
