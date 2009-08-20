package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.RenameablePackagingElement;

import javax.swing.tree.TreePath;

/**
 * @author nik
 */
public class RenamePackagingElementAction extends AnAction {
  private final ArtifactEditorEx myArtifactEditor;

  public RenamePackagingElementAction(ArtifactEditorEx artifactEditor) {
    super(ProjectBundle.message("action.name.rename.packaging.element"));
    registerCustomShortcutSet(CommonShortcuts.getRename(), artifactEditor.getLayoutTreeComponent().getTreePanel());
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElement<?> element = selection.getElementIfSingle();
    final boolean visible = element instanceof RenameablePackagingElement && ((RenameablePackagingElement)element).canBeRenamed();
    e.getPresentation().setEnabled(visible);
    e.getPresentation().setVisible(visible);
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node == null) return;
    final TreePath path = selection.getPath(node);
    myArtifactEditor.getLayoutTreeComponent().ensureRootIsWritable();
    myArtifactEditor.getLayoutTreeComponent().rename(path);
  }
}
