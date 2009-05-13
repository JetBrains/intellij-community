package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;

/**
* @author nik
*/
public class ArtifactEditorNavigateAction extends AnAction {
  private LayoutTreeComponent myLayoutTreeComponent;

  public ArtifactEditorNavigateAction(LayoutTreeComponent layoutTreeComponent) {
    super(ProjectBundle.message("action.name.facet.navigate"));
    registerCustomShortcutSet(CommonShortcuts.getEditSource(), layoutTreeComponent.getLayoutTree());
    myLayoutTreeComponent = layoutTreeComponent;
  }

  public void update(final AnActionEvent e) {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    e.getPresentation().setEnabled(node != null && node.getElementPresentation().canNavigateToSource());
  }

  public void actionPerformed(final AnActionEvent e) {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    if (node != null) {
      node.getElementPresentation().navigateToSource();
    }
  }
}
