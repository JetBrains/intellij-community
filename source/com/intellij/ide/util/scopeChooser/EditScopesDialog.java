/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;

/**
 * User: anna
 * Date: 03-Jul-2006
 */
public class EditScopesDialog extends SingleConfigurableEditor {
  private NamedScope mySelectedScope;
  private boolean myCheckShared;

  public EditScopesDialog(final Project project, final boolean checkShared) {
    super(project, ScopeChooserConfigurable.getInstance(project), "scopes");
    myCheckShared = checkShared;
  }

  protected void doOKAction() {
    final Object selectedObject = ((ScopeChooserConfigurable)getConfigurable()).getSelectedObject();
    if (selectedObject instanceof NamedScope){
      mySelectedScope = (NamedScope)selectedObject;
    }
    super.doOKAction();
    if (myCheckShared) {
      final Project project = getProject();
      final DependencyValidationManager manager = DependencyValidationManager.getInstance(project);
      NamedScope scope = manager.getScope(mySelectedScope.getName());
      if (scope == null) {
        if (Messages.showYesNoDialog(IdeBundle.message("scope.unable.to.save.scope.message"),
                                     IdeBundle.message("scope.unable.to.save.scope.title"), Messages.getErrorIcon()) == DialogWrapper
          .OK_EXIT_CODE) {
          final String newName = Messages.showInputDialog(project, IdeBundle.message("add.scope.name.label"),
                                                          IdeBundle.message("scopes.save.dialog.title.shared"), Messages.getQuestionIcon(),
                                                          mySelectedScope.getName(), new InputValidator() {
            public boolean checkInput(String inputString) {
              return inputString != null && inputString.length() > 0 && manager.getScope(inputString) == null;
            }

            public boolean canClose(String inputString) {
              return checkInput(inputString);
            }
          });
          if (newName != null) {
            final PackageSet packageSet = mySelectedScope.getValue();
            scope = new NamedScope(newName, packageSet != null ? packageSet.createCopy() : null);
            mySelectedScope = scope;
            manager.addScope(mySelectedScope);
          }
        }
      }
    }
  }


  public static EditScopesDialog editConfigurable(final Project project, final Runnable advancedInitialization){
    return editConfigurable(project, advancedInitialization, false);
  }

  public static EditScopesDialog editConfigurable(final Project project, final Runnable advancedInitialization, final boolean checkShared){
    final EditScopesDialog dialog = new EditScopesDialog(project, checkShared);
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
