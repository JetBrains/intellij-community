package com.intellij.ide.scopeView;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.packageDependencies.ui.ModuleNode;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.packageDependencies.ui.TreeModelBuilder;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

/**
 * User: anna
 * Date: 25-Jan-2006
 */
public class ScopeTreeViewPanel extends JPanel implements JDOMExternalizable, DataProvider {
  private MyPsiTreeChangeAdapter myPsiTreeChangeAdapter = new MyPsiTreeChangeAdapter();
  private ModuleRootListener myModuleRootListener = new MyModuleRootListener();

  private JTree myTree;
  private final Project myProject;
  private TreeModelBuilder myBuilder;
  private JComboBox myScopesCombo;
  private JPanel myPanel;

  public String CURRENT_SCOPE;

  private boolean myInitialized = false;


  public ScopeTreeViewPanel(final Project project) {
    super(new BorderLayout());
    myProject = project;
    initScopes();
    initTree();
    add(myPanel, BorderLayout.CENTER);
  }

  public void initListeners(){
    myInitialized = true;
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeAdapter);
    ProjectRootManager.getInstance(myProject).addModuleRootListener(myModuleRootListener);
  }

  public void dispose(){
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeAdapter);
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myModuleRootListener);
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  private void initScopes(){
    final DependencyValidationManager holder = DependencyValidationManager.getInstance(myProject);
    reloadScopes(holder);
    myScopesCombo.setRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component cellRendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof NamedScope){
          setText(((NamedScope)value).getName());
        }
        return cellRendererComponent;
      }
    });
    myScopesCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Object selectedItem = myScopesCombo.getSelectedItem();
        if (selectedItem instanceof NamedScope) {
          final NamedScope scope = (NamedScope)selectedItem;
          refreshScope(scope, holder, true);
          if (scope != holder.getProjectScope()) {
            CURRENT_SCOPE = scope.getName();
          }
        }
      }
    });
  }

  public JPanel getPanel() {
    return this;
  }

  private void initTree() {
    myTree.setCellRenderer(new MyTreeCellRenderer());
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);
    TreeToolTipHandler.install(myTree);
    TreeUtil.installActions(myTree);
    PopupHandler.installPopupHandler(myTree,
                                    (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_SCOPE_VIEW_POPUP),
                                    ActionPlaces.SCOPE_VIEW_POPUP, ActionManager.getInstance());
    EditSourceOnDoubleClickHandler.install(myTree);
  }

  private void refreshScope(NamedScope scope, final NamedScopesHolder holder, boolean showProgress) {
    final DefaultMutableTreeNode root = ((DefaultMutableTreeNode)myTree.getModel().getRoot());
    root.removeAllChildren();
    if (scope == null){ //was deleted
      scope = DependencyValidationManager.getInstance(myProject).getProjectScope();
      reloadScopes(holder);
    }
    final PackageSet packageSet = scope.getValue();
    final DependenciesPanel.DependencyPanelSettings settings = new DependenciesPanel.DependencyPanelSettings();
    settings.UI_FILTER_LEGALS = true;
    settings.UI_GROUP_BY_SCOPE_TYPE = false;
    settings.UI_SHOW_FILES = true;
    settings.UI_GROUP_BY_FILES = true;
    myBuilder = new TreeModelBuilder(myProject, false, new TreeModelBuilder.Marker() {
      public boolean isMarked(PsiFile file) {
        return packageSet.contains(file, holder);
      }
    }, settings);
    final TreeModelBuilder.TreeModel treeModel = myBuilder.build(myProject, showProgress);
    final DefaultMutableTreeNode scopeRootNode = (DefaultMutableTreeNode)treeModel.getRoot();
    for(int i = scopeRootNode.getChildCount() - 1; i >= 0; i--){
      root.add ((MutableTreeNode)scopeRootNode.getChildAt(i));
    }
    ((DefaultTreeModel)myTree.getModel()).reload();
  }

  public void reloadScopes(final NamedScopesHolder holder) {
    myScopesCombo.setModel(new DefaultComboBoxModel());
    final NamedScope[] scopes = holder.getScopes();
    for (NamedScope scope : scopes) {
      ((DefaultComboBoxModel)myScopesCombo.getModel()).addElement(scope);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void selectCurrentScope() {
    final DependencyValidationManager holder = DependencyValidationManager.getInstance(myProject);
    final NamedScope scope = CURRENT_SCOPE != null ? holder.getScope(CURRENT_SCOPE) : holder.getProjectScope();
    myScopesCombo.setSelectedItem(scope);
    refreshScope(scope, holder, true);
  }

  @Nullable
  public Object getData(String dataId) {
    if (dataId.equals(DataConstants.PROJECT)){
      return myProject;
    }
    if (dataId.equals(DataConstants.MODULE)){
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null){
        PackageDependenciesNode node = (PackageDependenciesNode)selectionPath.getLastPathComponent();
        if (node instanceof ModuleNode){
          return ((ModuleNode)node).getModule();
        } else {
          final PsiElement psiElement = node.getPsiElement();
          if (psiElement != null){
            return ModuleUtil.findModuleForPsiElement(psiElement);
          }
        }
      }
    }
    if (dataId.equals(DataConstants.PSI_ELEMENT)){
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null){
        PackageDependenciesNode node = (PackageDependenciesNode)selectionPath.getLastPathComponent();
        return node != null ? node.getPsiElement() : null;
      }
    }
    final TreePath[] treePaths = myTree.getSelectionPaths();
    if (treePaths != null){
      if (dataId.equals(DataConstantsEx.PSI_ELEMENT_ARRAY)) {
        Set<PsiElement> psiElements = new HashSet<PsiElement>();
        for (TreePath treePath : treePaths) {
          final PackageDependenciesNode node = (PackageDependenciesNode)treePath.getLastPathComponent();
          final PsiElement psiElement = node.getPsiElement();
          if (psiElement != null){
            psiElements.add(psiElement);
          }
        }
        return psiElements.isEmpty() ? null : psiElements.toArray(new PsiElement[psiElements.size()]);
      }
    }
    return null;
  }

  private static class MyTreeCellRenderer extends DefaultTreeCellRenderer {
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      if (value instanceof PackageDependenciesNode) {
        PackageDependenciesNode node = (PackageDependenciesNode)value;
        if (expanded) {
          setIcon(node.getOpenIcon());
        }
        else {
          setIcon(node.getClosedIcon());
        }
      }
      return this;
    }
  }

  private class MyPsiTreeChangeAdapter extends PsiTreeChangeAdapter{

    public void childAdded(PsiTreeChangeEvent event) {
      final PsiElement element = event.getParent();
      if (element instanceof PsiDirectory || element instanceof PsiPackage){
        final PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          final PackageDependenciesNode dirNode = myBuilder.addFileNode((PsiFile)child);
          if (dirNode != null) {
            ((DefaultTreeModel)myTree.getModel()).reload(dirNode);
          }
        }
      }
    }

    public void childRemoved(PsiTreeChangeEvent event) {
      final PsiElement child = event.getChild();
      final PsiElement parent = event.getParent();
      if (parent instanceof PsiDirectory && (child instanceof PsiFile || child instanceof PsiDirectory)) {
        final PackageDependenciesNode node = myBuilder.removeNode(child, (PsiDirectory)parent);
        if (node != null){
          ((DefaultTreeModel)myTree.getModel()).reload(node);
        }
      }
    }

    public void childMoved(PsiTreeChangeEvent event) {
      final PsiElement oldParent = event.getOldParent();
      final PsiElement newParent = event.getNewParent();
      PsiElement child = event.getChild();
      if (oldParent instanceof PsiDirectory && newParent instanceof PsiDirectory){
        if (child instanceof PsiFile) {
          myBuilder.removeNode(child, (PsiDirectory)oldParent);
          myBuilder.addFileNode((PsiFile)child);
        }
      }
    }
  }

  private class MyModuleRootListener implements ModuleRootListener{
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    public void rootsChanged(ModuleRootEvent event) {
      final DependencyValidationManager holder = DependencyValidationManager.getInstance(myProject);
      refreshScope(holder.getScope(CURRENT_SCOPE), holder, false);
    }
  }
}
