package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.util.Function;

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
    final LayoutTreeSelection selection = myEditor.getPackagingElementsTree().getSelection();
    e.getPresentation().setEnabled(selection.getCommonParentElement() != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeComponent treeComponent = myEditor.getPackagingElementsTree();
    final LayoutTreeSelection selection = treeComponent.getSelection();
    final CompositePackagingElement<?> oldParent = selection.getCommonParentElement();
    if (oldParent == null) return;
    final Function<PackagingElement<?>,PackagingElement<?>> map = treeComponent.ensureRootIsWritable();
    final CompositePackagingElement<?> parent = (CompositePackagingElement<?>)map.fun(oldParent);
    if (parent == null) return;

    final Collection<? extends PackagingElement> selectedElements = selection.getSelectedElements();
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
