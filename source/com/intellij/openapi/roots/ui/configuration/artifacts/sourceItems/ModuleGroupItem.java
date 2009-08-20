package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleGroupItem extends PackagingSourceItem {
  private final String myGroupName;
  private final String[] myPath;

  public ModuleGroupItem(String[] path) {
    super(false);
    myGroupName = path[path.length - 1];
    myPath = path;
  }

  public boolean equals(Object obj) {
    return obj instanceof ModuleGroupItem && Comparing.equal(myPath, ((ModuleGroupItem)obj).myPath);
  }

  public int hashCode() {
    return Arrays.hashCode(myPath);
  }

  public SourceItemPresentation createPresentation(@NotNull PackagingEditorContext context) {
    return new ModuleGroupSourceItemPresentation(myGroupName);
  }

  @NotNull
  @Override
  public List<? extends PackagingElement<?>> createElements(@NotNull PackagingEditorContext context) {
    return Collections.emptyList();
  }

  public String[] getPath() {
    return myPath;
  }

  private static class ModuleGroupSourceItemPresentation extends SourceItemPresentation {
    private final String myGroupName;

    public ModuleGroupSourceItemPresentation(String groupName) {
      myGroupName = groupName;
    }

    @Override
    public String getPresentableName() {
      return myGroupName;
    }

    @Override
    public void render(@NotNull PresentationData presentationData) {
      presentationData.setClosedIcon(Icons.CLOSED_MODULE_GROUP_ICON);
      presentationData.setOpenIcon(Icons.OPENED_MODULE_GROUP_ICON);
      presentationData.addText(myGroupName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    @Override
    public int getWeight() {
      return SourceItemWeights.MODULE_GROUP_WEIGHT;
    }
  }
}
