package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditorImpl;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.TreeNodePresentation;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class SourceItemsTreeRoot extends SourceItemNodeBase {
  public SourceItemsTreeRoot(PackagingEditorContext context, ArtifactsEditorImpl artifactsEditor) {
    super(context, null, new RootNodePresentation(), artifactsEditor);
  }

  protected PackagingSourceItem getSourceItem() {
    return null;
  }

  @Override
  public Object[] getEqualityObjects() {
    return new Object[]{"root"};
  }

  @Override
  public boolean isAutoExpandNode() {
    return true;
  }

  private static class RootNodePresentation extends TreeNodePresentation {
    @Override
    public String getPresentableName() {
      return "";
    }

    @Override
    public void render(@NotNull PresentationData presentationData) {
    }

    @Override
    public int getWeight() {
      return 0;
    }
  }
}
