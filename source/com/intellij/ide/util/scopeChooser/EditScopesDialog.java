/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.packageSet.NamedScope;

/**
 * User: anna
 * Date: 03-Jul-2006
 */
public class EditScopesDialog extends SingleConfigurableEditor {
  private NamedScope mySelectedScope;

  public EditScopesDialog(final Project project) {
    super(project, ScopeChooserConfigurable.getInstance(project), "scopes");
  }

  protected void doOKAction() {
    final Object selectedObject = ((ScopeChooserConfigurable)getConfigurable()).getSelectedObject();
    if (selectedObject instanceof NamedScope){
      mySelectedScope = (NamedScope)selectedObject;
    }
    super.doOKAction();
  }


  public static EditScopesDialog editConfigurable(final Project project, final Runnable advancedInitialization){
    final EditScopesDialog dialog = new EditScopesDialog(project);
    if (advancedInitialization != null) {
      advancedInitialization.run();
    }
    dialog.show();
    return dialog;
  }

  public NamedScope getSelectedScope() {
    return mySelectedScope;
  }
}
