package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.ide.projectView.PresentationData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DelegatedSourceItemPresentation extends SourceItemPresentation {
  private TreeNodePresentation myPresentation;

  public DelegatedSourceItemPresentation(TreeNodePresentation presentation) {
    myPresentation = presentation;
  }

  public String getPresentableName() {
    return myPresentation.getPresentableName();
  }

  public String getSearchName() {
    return myPresentation.getSearchName();
  }

  public void render(@NotNull PresentationData presentationData) {
    myPresentation.render(presentationData);
  }

  @Nullable
  public String getTooltipText() {
    return myPresentation.getTooltipText();
  }

  public boolean canNavigateToSource() {
    return myPresentation.canNavigateToSource();
  }

  public void navigateToSource() {
    myPresentation.navigateToSource();
  }

  @Nullable
  public Object getSourceObject() {
    return myPresentation.getSourceObject();
  }

  public int getWeight() {
    return myPresentation.getWeight();
  }
}
