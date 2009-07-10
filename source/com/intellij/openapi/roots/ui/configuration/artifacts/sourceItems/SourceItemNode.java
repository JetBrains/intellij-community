package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.treeStructure.SimpleTree;

import java.awt.event.InputEvent;
import java.util.Collections;

/**
 * @author nik
 */
public class SourceItemNode extends SourceItemNodeBase {
  private final PackagingSourceItem mySourceItem;

  public SourceItemNode(PackagingEditorContext context, NodeDescriptor parentDescriptor, PackagingSourceItem sourceItem, ArtifactEditorEx artifactEditor) {
    super(context, parentDescriptor, sourceItem.createPresentation(context), artifactEditor);
    mySourceItem = sourceItem;
  }

  @Override
  public Object[] getEqualityObjects() {
    return new Object[]{mySourceItem};
  }

  @Override
  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
    if (mySourceItem.isProvideElements() && getChildren().length == 0) {
      getArtifactEditor().getLayoutTreeComponent().putIntoDefaultLocations(Collections.singletonList(mySourceItem));
    }
  }

  protected PackagingSourceItem getSourceItem() {
    return mySourceItem;
  }
}
