package com.intellij.ide.hierarchy.type;

import com.intellij.openapi.util.IconLoader;

/**
 * @author cdr
 */
public final class ViewSupertypesHierarchyAction extends ChangeViewTypeActionBase {
  public ViewSupertypesHierarchyAction() {
    super("Supertypes Hierarchy", "Switch to Supertypes Hierarchy", IconLoader.getIcon("/hierarchy/supertypes.png"));
  }

  protected final String getTypeName() {
    return SupertypesHierarchyTreeStructure.TYPE;
  }
}
