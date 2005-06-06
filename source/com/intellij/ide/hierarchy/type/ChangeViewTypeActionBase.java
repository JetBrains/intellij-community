package com.intellij.ide.hierarchy.type;

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;

/**
 * @author cdr
 */
abstract class ChangeViewTypeActionBase extends ToggleAction {
  protected TypeHierarchyBrowser myTypeHierarchyBrowser;

  public ChangeViewTypeActionBase(final String shortDescription, final String longDescription, final Icon icon) {
    super(shortDescription, longDescription, icon);
  }

  public final boolean isSelected(final AnActionEvent event) {
    return myTypeHierarchyBrowser != null && getTypeName().equals(myTypeHierarchyBrowser.getCurrentViewName());
  }

  protected abstract String getTypeName();

  public final void setSelected(final AnActionEvent event, final boolean flag) {
    if (flag) {
      //        setWaitCursor();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myTypeHierarchyBrowser != null) {
            myTypeHierarchyBrowser.changeView(getTypeName());
            myTypeHierarchyBrowser = null;
          }
        }
      });
    }
  }

  public void update(final AnActionEvent event) {
    myTypeHierarchyBrowser = (TypeHierarchyBrowser)event.getDataContext().getData(TypeHierarchyBrowser.TYPE_HIERARCHY_BROWSER_ID);
    // its important to assign the myTypeHierarchyBrowser first
    super.update(event);
    final Presentation presentation = event.getPresentation();
    presentation.setEnabled(myTypeHierarchyBrowser != null && myTypeHierarchyBrowser.isValidBase());
  }
}
