package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditor;

/**
 * @author nik
 */
public class SourceItemNode extends SourceItemNodeBase {
  private final PackagingSourceItem mySourceItem;

  public SourceItemNode(PackagingEditorContext context, NodeDescriptor parentDescriptor, PackagingSourceItem sourceItem, ArtifactEditor artifactEditor) {
    super(context, parentDescriptor, sourceItem.createPresentation(context), artifactEditor);
    mySourceItem = sourceItem;
  }

  @Override
  public Object[] getEqualityObjects() {
    return new Object[]{mySourceItem};
  }

  protected PackagingSourceItem getSourceItem() {
    return mySourceItem;
  }
}
