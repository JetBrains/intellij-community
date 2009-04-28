package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.util.Function;

import javax.swing.tree.TreeNode;
import java.util.Collection;

/**
 * @author nik
 */
public class InlineArtifactAction extends AnAction {
  private final ArtifactsEditorImpl myEditor;

  public InlineArtifactAction(ArtifactsEditorImpl editor) {
    super("Inline Artifact");
    myEditor = editor;
  }

  @Override
  public void update(AnActionEvent e) {
    final Collection<? extends PackagingElement> elements = myEditor.getPackagingElementsTree().getSelectedElements();
    e.getPresentation().setEnabled(elements.size() == 1 && elements.iterator().next() instanceof ArtifactPackagingElement);
  }

  public void actionPerformed(AnActionEvent e) {
    final PackagingElementsTree tree = myEditor.getPackagingElementsTree();
    final PackagingElementNode[] nodes = tree.getSelectedNodes();
    if (nodes.length != 1) return;
    final PackagingElement<?> element = nodes[0].getPackagingElement();
    if (!(element instanceof ArtifactPackagingElement)) return;
    final TreeNode parentNode = nodes[0].getParent();
    if (!(parentNode instanceof PackagingElementNode)) return;

    final Function<PackagingElement<?>,PackagingElement<?>> map = tree.ensureRootIsWritable();
    CompositePackagingElement<?> parent = (CompositePackagingElement<?>)map.fun(((PackagingElementNode)parentNode).getPackagingElement());
    if (parent == null) {
      //todo[nik] parent from included content
      return;
    }
    parent.removeChild(map.fun(element));
    final Artifact artifact = ((ArtifactPackagingElement)element).findArtifact(myEditor.getContext());
    if (artifact != null) {
      for (PackagingElement<?> child : artifact.getRootElement().getChildren()) {
        parent.addChild(ArtifactUtil.copyWithChildren(child));
      }
    }
    tree.rebuildTree();
  }
}
