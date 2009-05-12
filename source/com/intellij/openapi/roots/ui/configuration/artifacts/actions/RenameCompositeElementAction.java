package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditor;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.packaging.elements.CompositePackagingElement;

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
    e.getPresentation().setVisible(selection.getElementIfSingle() instanceof CompositePackagingElement);
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactsEditor.getPackagingElementsTree().getSelection();
    final TreePath path = selection.getPath(selection.getSelectedNodes().get(0));
    myArtifactsEditor.getPackagingElementsTree().rename(path);
  }
}
