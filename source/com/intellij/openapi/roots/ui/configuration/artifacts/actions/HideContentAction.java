package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditor;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingNodeSource;

import java.util.Collection;

/**
 * @author nik
 */
public class HideContentAction extends AnAction {
  private ArtifactEditor myArtifactEditor;

  public HideContentAction(ArtifactEditor artifactEditor) {
    super("Hide Content");
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getPackagingElementsTree().getSelection();
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
    final LayoutTreeSelection selection = myArtifactEditor.getPackagingElementsTree().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node == null) return;

    final Collection<PackagingNodeSource> sources = node.getNodeSources();
    for (PackagingNodeSource source : sources) {
      myArtifactEditor.getSubstitutionParameters().dontSubstitute(source.getSourceElement());
      myArtifactEditor.getPackagingElementsTree().getLayoutTree().addSubtreeToUpdate(source.getSourceParentNode());
    }
  }
}
