package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.Collection;

/**
 * @author nik
 */
public class ExtractArtifactAction extends AnAction {
  private ArtifactsEditorImpl myEditor;

  public ExtractArtifactAction(ArtifactsEditorImpl editor) {
    super(ProjectBundle.message("action.name.extract.artifact"));
    myEditor = editor;
  }

  @Override
  public void update(AnActionEvent e) {
    final PackagingElementNode[] selectedNodes = myEditor.getPackagingElementsTree().getSelectedNodes();
    e.getPresentation().setEnabled(getCommonParent(selectedNodes) != null);
  }

  @Nullable
  private static CompositePackagingElement<?> getCommonParent(PackagingElementNode[] nodes) {
    PackagingElementNode common = null;
    for (PackagingElementNode node : nodes) {
      final TreeNode parent = node.getParent();
      if (!(parent instanceof PackagingElementNode)) return null;
      if (common == null) {
        common = (PackagingElementNode)parent;
      }
      else if (common != parent) return null;
    }
    if (common == null) return null;
    final PackagingElement<?> element = common.getPackagingElement();
    return element instanceof CompositePackagingElement<?> ? (CompositePackagingElement<?>)element : null;
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeComponent treeComponent = myEditor.getPackagingElementsTree();
    final CompositePackagingElement<?> oldParent = getCommonParent(treeComponent.getSelectedNodes());
    if (oldParent == null) return;
    final Function<PackagingElement<?>,PackagingElement<?>> map = treeComponent.ensureRootIsWritable();
    final CompositePackagingElement<?> parent = (CompositePackagingElement<?>)map.fun(oldParent);
    if (parent == null) return;

    final Collection<? extends PackagingElement> selectedElements = treeComponent.getSelectedElements();
    final String name = Messages.showInputDialog(myEditor.getMainComponent(), ProjectBundle.message("label.text.specify.artifact.name"),
                                                 ProjectBundle.message("dialog.title.extract.artifact"), null);
    if (name != null) {
      //todo[nik] select type?
      final ModifiableArtifact artifact = myEditor.getContext().getModifiableArtifactModel().addArtifact(name, PlainArtifactType.getInstance());
      for (PackagingElement<?> element : selectedElements) {
        artifact.getRootElement().addChild(ArtifactUtil.copyWithChildren(element));
      }
      for (PackagingElement element : selectedElements) {
        parent.removeChild(map.fun(element));
      }
      parent.addChild(new ArtifactPackagingElement(name));
      treeComponent.rebuildTree();
    }
  }
}
