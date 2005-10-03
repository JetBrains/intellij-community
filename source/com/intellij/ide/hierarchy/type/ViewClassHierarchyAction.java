package com.intellij.ide.hierarchy.type;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ide.IdeBundle;

/**
 * @author cdr
 */
public final class ViewClassHierarchyAction extends ChangeViewTypeActionBase {
  public ViewClassHierarchyAction() {
    super(IdeBundle.message("action.view.class.hierarchy"),
          IdeBundle.message("action.description.view.class.hierarchy"), IconLoader.getIcon("/hierarchy/class.png"));
  }

  protected final String getTypeName() {
    return TypeHierarchyTreeStructure.TYPE;
  }

  public final void update(final AnActionEvent event) {
    super.update(event);
    event.getPresentation().setEnabled(myTypeHierarchyBrowser != null && !myTypeHierarchyBrowser.isInterface());
  }
}
