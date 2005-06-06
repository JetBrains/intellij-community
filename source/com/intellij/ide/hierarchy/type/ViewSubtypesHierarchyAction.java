package com.intellij.ide.hierarchy.type;

import com.intellij.openapi.util.IconLoader;

/**
 * @author cdr
 */
public final class ViewSubtypesHierarchyAction extends ChangeViewTypeActionBase {
  public ViewSubtypesHierarchyAction() {
    super("Subtypes Hierarchy", "Switch to Subtypes Hierarchy", IconLoader.getIcon("/hierarchy/subtypes.png"));
  }

  protected final String getTypeName() {
    return SubtypesHierarchyTreeStructure.TYPE;
  }
}
