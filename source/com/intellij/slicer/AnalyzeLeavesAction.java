package com.intellij.slicer;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.util.Icons;

/**
 * @author cdr
 */
public class AnalyzeLeavesAction extends ToggleAction {
  private final SliceTreeBuilder myTreeBuilder;

  public AnalyzeLeavesAction(SliceTreeBuilder treeBuilder) {
    super("Group by leaf expression", "sdfsdfsdfsdf", Icons.XML_TAG_ICON);
    myTreeBuilder = treeBuilder;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myTreeBuilder.splitByLeafExpressions;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (state) {
      myTreeBuilder.switchToSplittedNodes();
    }
    else {
      myTreeBuilder.switchToUnsplittedNodes();
    }
  }
}
