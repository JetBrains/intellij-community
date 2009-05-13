package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditor;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingNodeSource;

import java.util.Collection;

/**
 * @author nik
 */
public class HideContentAction extends AnAction {
  private ArtifactsEditor myArtifactsEditor;

  public HideContentAction(ArtifactsEditor artifactsEditor) {
    super("Hide Content");
    myArtifactsEditor = artifactsEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactsEditor.getPackagingElementsTree().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node != null) {
      final Collection<PackagingNodeSource> sources = node.getNodeSources();
      if (!sources.isEmpty()) {
        StringBuilder description = new StringBuilder();
        for (PackagingNodeSource source : sources) {
          if (description.length() > 0) description.append(", ");
          description.append("'").append(source.getPresentableName()).append("'");
        }
        e.getPresentation().setVisible(true);
        e.getPresentation().setText("Hide Content of " + description);
        return;
      }
    }
    e.getPresentation().setVisible(false);
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactsEditor.getPackagingElementsTree().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node == null) return;

    final Collection<PackagingNodeSource> sources = node.getNodeSources();
    for (PackagingNodeSource source : sources) {
      myArtifactsEditor.getSubstitutionParameters().dontSubstitute(source.getSourceElement());
      myArtifactsEditor.getPackagingElementsTree().getLayoutTree().addSubtreeToUpdate(source.getSourceParentNode());
    }
  }
}
