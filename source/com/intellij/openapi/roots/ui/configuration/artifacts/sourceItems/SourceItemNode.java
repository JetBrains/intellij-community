package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditor;

/**
 * @author nik
 */
public class SourceItemNode extends SourceItemNodeBase {
  private final PackagingSourceItem mySourceItem;

  public SourceItemNode(PackagingEditorContext context, NodeDescriptor parentDescriptor, PackagingSourceItem sourceItem, ArtifactsEditor artifactsEditor) {
    super(context, parentDescriptor, sourceItem.createPresentation(context), artifactsEditor);
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
