package com.intellij.ide.hierarchy.type;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ide.IdeBundle;

/**
 * @author cdr
 */
public final class ViewSupertypesHierarchyAction extends ChangeViewTypeActionBase {
  public ViewSupertypesHierarchyAction() {
    super(IdeBundle.message("action.view.supertypes.hierarchy"), 
          IdeBundle.message("action.description.view.supertypes.hierarchy"), IconLoader.getIcon("/hierarchy/supertypes.png"));
  }

  protected final String getTypeName() {
    return SupertypesHierarchyTreeStructure.TYPE;
  }
}
