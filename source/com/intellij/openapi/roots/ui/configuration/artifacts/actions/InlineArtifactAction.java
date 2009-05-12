package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.util.Function;

/**
 * @author nik
 */
public class InlineArtifactAction extends AnAction {
  private final ArtifactsEditorImpl myEditor;

  public InlineArtifactAction(ArtifactsEditorImpl editor) {
    super(ProjectBundle.message("action.name.inline.artifact"));
    myEditor = editor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myEditor.getPackagingElementsTree().getSelection();
    PackagingElement<?> element = selection.getElementIfSingle();
    e.getPresentation().setEnabled(element instanceof ArtifactPackagingElement);
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeComponent treeComponent = myEditor.getPackagingElementsTree();
    final LayoutTreeSelection selection = treeComponent.getSelection();
    final PackagingElement<?> element = selection.getElementIfSingle();
    if (!(element instanceof ArtifactPackagingElement)) return;

    final Function<PackagingElement<?>,PackagingElement<?>> map = treeComponent.ensureRootIsWritable();
    CompositePackagingElement<?> parent = (CompositePackagingElement<?>)map.fun(element);
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
    treeComponent.rebuildTree();
  }
}
