package com.intellij.ide.hierarchy.type;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ide.IdeBundle;

/**
 * @author cdr
 */
public final class ViewSubtypesHierarchyAction extends ChangeViewTypeActionBase {
  public ViewSubtypesHierarchyAction() {
    super(IdeBundle.message("action.view.subtypes.hierarchy"),
          IdeBundle.message("action.description.view.subtypes.hierarchy"), IconLoader.getIcon("/hierarchy/subtypes.png"));
  }

  protected final String getTypeName() {
    return SubtypesHierarchyTreeStructure.TYPE;
  }
}
