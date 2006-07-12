/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.scopeChooser;

import com.intellij.CommonBundle;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: anna
 * Date: 01-Jul-2006
 */
public class ScopeChooserConfigurable extends MasterDetailsComponent implements ProjectComponent {
  private static final Icon SHARED_SCOPES = IconLoader.getIcon("/ide/sharedScope.png");
  private static final Icon LOCAL_SCOPES = IconLoader.getIcon("/ide/localScope.png");
  private static final Icon SCOPES = IconLoader.getIcon("/ide/scopeConfigurable.png");

  private NamedScopeManager myLocalScopesManager;
  private DependencyValidationManager mySharedScopesManager;

  private Project myProject;

  private MyNode myLocalScopesNode;
  private MyNode mySharedScopesNode;

  public static ScopeChooserConfigurable getInstance(Project project) {
    return project.getComponent(ScopeChooserConfigurable.class);
  }

  public ScopeChooserConfigurable(final Project project) {
    myLocalScopesManager = NamedScopeManager.getInstance(project);
    mySharedScopesManager = DependencyValidationManager.getInstance(project);
    myProject = project;
    initTree();
  }

  protected ArrayList<AnAction> createActions() {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(new MyAddAction());
    result.add(new MyDeleteAction(new Condition<Object>() {
      public boolean value(final Object o) {
        if (o instanceof MyNode) {
          final Object editableObject = ((MyNode)o).getConfigurable().getEditableObject();
          return editableObject instanceof NamedScope;
        }
        return false;
      }
    }));
    return result;
  }

  protected ArrayList<AnAction> getAdditionalActions() {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(new MyRenameAction());
    return result;
  }

  public void disposeUIResources() {
    super.disposeUIResources();
    myLocalScopesNode = null;
    mySharedScopesNode = null;
  }


  public void reset() {
    reloadTree();
    super.reset();
  }


  public void apply() throws ConfigurationException {
    super.apply();
    processScopes(myLocalScopesManager, myLocalScopesNode);
    processScopes(mySharedScopesManager, mySharedScopesNode);
  }


  public boolean isModified() {
    if (super.isModified()) return true;
    if (isScopesModified(myLocalScopesManager, myLocalScopesNode)) return true;
    if (isScopesModified(mySharedScopesManager, mySharedScopesNode)) return true;
    return false;
  }

  private static boolean isScopesModified(NamedScopesHolder holder, MyNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      final MyNode node = (MyNode)root.getChildAt(i);
      final NamedScope namedScope = (NamedScope)node.getConfigurable().getEditableObject();
      final NamedScope scope = holder.getScope(namedScope.getName());
      if (scope == null) return true;
      final PackageSet set = scope.getValue();
      final PackageSet packageSet = namedScope.getValue();
      if (packageSet == null && set != null) return true;
      if (set == null && packageSet != null) return true;
      if (set != null) {
        if (!Comparing.strEqual(set.getText(), packageSet.getText())) return true;
      }
    }
    return false;
  }

  private static void processScopes(NamedScopesHolder holder, MyNode root) {
    holder.removeAllSets();
    for (int i = 0; i < root.getChildCount(); i++) {
      final MyNode node = (MyNode)root.getChildAt(i);
      holder.addScope(((NamedScope)node.getConfigurable().getEditableObject()));
    }
  }

  private void reloadTree() {
    myLocalScopesNode = new MyNode(new ScopesGroupConfigurable(myLocalScopesManager, LOCAL_SCOPES), false);
    loadScopes(myLocalScopesManager, myLocalScopesNode, LOCAL_SCOPES);
    myRoot.add(myLocalScopesNode);

    mySharedScopesNode = new MyNode(new ScopesGroupConfigurable(mySharedScopesManager, SHARED_SCOPES), false);
    loadScopes(mySharedScopesManager, mySharedScopesNode, SHARED_SCOPES);
    myRoot.add(mySharedScopesNode);
  }

  private void loadScopes(final NamedScopesHolder holder, final MyNode localScopesNode, final Icon icon) {
    final NamedScope[] scopes = holder.getScopes();
    for (NamedScope scope : scopes) {
      if (isPredefinedScope(scope)) continue;
      localScopesNode.add(new MyNode(new ScopeConfigurable(scope, myProject, myLocalScopesManager, icon), true));
    }
  }

  private boolean isPredefinedScope(final NamedScope scope) {
    return getPredefinedScopes(myProject).contains(scope);
  }

  private static Collection<NamedScope> getPredefinedScopes(Project project) {
    final Collection<NamedScope> result = new ArrayList<NamedScope>();
    final NamedScopesHolder[] holders = project.getComponents(NamedScopesHolder.class);
    for (NamedScopesHolder holder : holders) {
      final List<NamedScope> predefinedScopes = holder.getPredefinedScopes();
      if (predefinedScopes != null) {
        result.addAll(predefinedScopes);
      }
    }
    return result;
  }

  protected void initTree() {
    super.initTree();
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    });
  }

  protected void processRemovedItems() {
    //do nothing
  }

  protected boolean wasObjectStored(Object editableObject) {
    if (editableObject instanceof NamedScope) {
      NamedScope scope = (NamedScope)editableObject;
      final String scopeName = scope.getName();
      return myLocalScopesManager.getScope(scopeName) != null || mySharedScopesManager.getScope(scopeName) != null;
    }
    return false;
  }

  public String getDisplayName() {
    return IdeBundle.message("scopes.display.name");
  }

  public Icon getIcon() {
    return SCOPES;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;  //todo help id
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private String createUniqueName() {
    String str = InspectionsBundle.message("inspection.profile.unnamed");
    final HashSet<String> treeScopes = new HashSet<String>();
    obtainCurrentScopes(treeScopes, myLocalScopesNode);
    obtainCurrentScopes(treeScopes, mySharedScopesNode);
    if (!treeScopes.contains(str)) return str;
    int i = 1;
    while (true) {
      if (!treeScopes.contains(str + i)) return str + i;
      i++;
    }
  }

  private static void obtainCurrentScopes(final HashSet<String> scopes, final MyNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      final MyNode node = ((MyNode)root.getChildAt(i));
      final NamedScope scope = ((NamedScope)node.getConfigurable().getEditableObject());
      scopes.add(scope.getName());
    }
  }



  private class MyAddAction extends AnAction {

    public MyAddAction() {
      super(CommonBundle.message("button.add"), CommonBundle.message("button.add"), Icons.ADD_ICON);
      registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
    }

    public void actionPerformed(AnActionEvent e) {
      final String localScopeChoice = IdeBundle.message("add.local.scope.action.text");
      final String sharedScopeChoice = IdeBundle.message("add.shared.scope.action.text");
      JBPopupFactory.getInstance()
        .createWizardStep(new BaseListPopupStep<String>(IdeBundle.message("add.scope.popup.title"), new String[]{localScopeChoice, sharedScopeChoice}) {
          public PopupStep onChosen(final String s, final boolean finalChoice) {
            if (Comparing.strEqual(s, localScopeChoice)){
              addScope(myLocalScopesManager, myLocalScopesNode, LOCAL_SCOPES);
            } else {
              addScope(mySharedScopesManager, mySharedScopesNode, SHARED_SCOPES);
            }
            return PopupStep.FINAL_CHOICE;
          }

          public int getDefaultOptionIndex() {
            final TreePath selectionPath = myTree.getSelectionPath();
            if (selectionPath != null){
              final MyNode node = (MyNode)selectionPath.getLastPathComponent();
              Object editableObject = node.getConfigurable().getEditableObject();
              if (editableObject instanceof NamedScope){
                editableObject = ((MyNode)node.getParent()).getConfigurable().getEditableObject();
              }
              if (editableObject instanceof NamedScopeManager){
                return 0;
              } else if (editableObject instanceof DependencyValidationManager){
                return 1;
              }
            }
            return 0;
          }
        })
        .showUnderneathOf(myNorthPanel);
    }

    private void addScope(NamedScopesHolder holder, MyNode root, Icon icon) {
      final String newName = Messages.showInputDialog(myWholePanel,
                                                      IdeBundle.message("add.scope.name.label"),
                                                      IdeBundle.message("add.scope.dialog.title"), Messages.getInformationIcon(),
                                                      createUniqueName(), new InputValidator() {
        public boolean checkInput(String inputString) {
          return inputString != null && inputString.trim().length() > 0;
        }

        public boolean canClose(String inputString) {
          return checkInput(inputString);
        }
      });
      if (newName != null) {
        final NamedScope scope = new NamedScope(newName, null);
        final MyNode nodeToAdd = new MyNode(new ScopeConfigurable(scope, myProject, holder, icon), true);
        addNode(nodeToAdd, root);
        selectNodeInTree(nodeToAdd);
      }
    }
  }
}
