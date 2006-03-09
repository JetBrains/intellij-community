package com.intellij.ide.scopeView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ui.FileNode;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.util.Alarm;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author cdr
 */
public class ScopeViewPane extends AbstractProjectViewPane implements ProjectComponent {
  @NonNls public static final String ID = "Scope";
  private final ProjectView myProjectView;
  private ScopeTreeViewPanel myViewPanel;
  private final DependencyValidationManager myDependencyValidationManager;
  private NamedScopesHolder.ScopeListener myScopeListener;
  public static final Icon ICON = IconLoader.getIcon("/general/ideOptions.png");

  public static ScopeViewPane getInstance(Project project) {
    return project.getComponent(ScopeViewPane.class);
  }

  protected ScopeViewPane(final Project project, ProjectView projectView, DependencyValidationManager dependencyValidationManager) {
    super(project);
    myProjectView = projectView;
    myDependencyValidationManager = dependencyValidationManager;
    myScopeListener = new NamedScopesHolder.ScopeListener() {
      Alarm refreshProjectViewAlarm = new Alarm();
      public void scopesChanged() {
        // amortize batch scope changes
        refreshProjectViewAlarm.cancelAllRequests();
        refreshProjectViewAlarm.addRequest(new Runnable(){
          public void run() {
            myProjectView.removeProjectPane(ScopeViewPane.this);
            myProjectView.addProjectPane(ScopeViewPane.this);
          }
        },10);
      }
    };
    myDependencyValidationManager.addScopeListener(myScopeListener);
  }

  public String getTitle() {
    return IdeBundle.message("scope.view.title");
  }

  public Icon getIcon() {
    return ICON;
  }

  @NotNull
  public String getId() {
    return ID;
  }

  public JComponent createComponent() {
    myViewPanel = new ScopeTreeViewPanel(myProject);
    myViewPanel.initListeners();
    updateFromRoot(true);

    myTree = myViewPanel.getTree();
    installTreePopupHandler(ActionPlaces.SCOPE_VIEW_POPUP, IdeActions.GROUP_SCOPE_VIEW_POPUP);

    restoreState();
    return myViewPanel.getPanel();
  }

  public void dispose() {
    myViewPanel.dispose();
    myViewPanel = null;
    super.dispose();
  }

  @NotNull
  public String[] getSubIds() {
    final NamedScope[] scopes = myDependencyValidationManager.getScopes();
    String[] ids = new String[scopes.length];
    for (int i = 0; i < scopes.length; i++) {
      final NamedScope scope = scopes[i];
      ids[i] = scope.getName();
    }
    return ids;
  }

  @NotNull
  public String getPresentableSubIdName(@NotNull final String subId) {
    return subId;
  }

  public void addToolbarActions(DefaultActionGroup actionGroup) {
    actionGroup.add(ActionManager.getInstance().getAction("ScopeView.EditScopes"));
  }

  public void updateFromRoot(boolean restoreExpandedPaths) {
    NamedScope scope = myDependencyValidationManager.getScope(getSubId());
    myViewPanel.selectScope(scope);
  }

  public void select(Object element, VirtualFile file, boolean requestFocus) {
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return;

    List<NamedScope> allScopes = new ArrayList<NamedScope>();
    allScopes.addAll(Arrays.asList(myDependencyValidationManager.getScopes()));
    for (int i = 0; i < allScopes.size(); i++) {
      final NamedScope scope = allScopes.get(i);
      String name = scope.getName();
      if (name.equals(getSubId())) {
        allScopes.set(i, allScopes.get(0));
        allScopes.set(0, scope);
        break;
      }
    }
    for (NamedScope scope : allScopes) {
      String name = scope.getName();
      PackageSet packageSet = scope.getValue();
      if (packageSet.contains(psiFile, myDependencyValidationManager)) {
        if (!name.equals(getSubId())) {
          myProjectView.changeView(getId(), name);
        }
        PackageDependenciesNode node = myViewPanel.findNode(psiFile);
        if (node != null) {
          TreePath path = new TreePath(node.getPath());
          // hack: as soon as file path gets expanded, file node replaced with class node on the fly
          if (node instanceof FileNode && psiFile instanceof PsiJavaFile) {
            PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
            if (classes.length != 0) {
              ClassNode classNode = new ClassNode(classes[0]);
              path = path.getParentPath().pathByAddingChild(classNode);
            }
          }
          TreeUtil.selectPath(myTree, path);
        }
        break;
      }
    }
  }

  public int getWeight() {
    return 3;
  }

  public SelectInTarget createSelectInTarget() {
    return new ScopePaneSelectInTarget(myProject);
  }

  // project component related
  public void projectOpened() {
    myProjectView.addProjectPane(this);
  }

  public void projectClosed() {
  }

  @NonNls
  public String getComponentName() {
    return "ScopeViewComponent";
  }

  public void initComponent() {

  }

  public void disposeComponent() {
    myDependencyValidationManager.removeScopeListener(myScopeListener);
  }

  protected Object exhumeElementFromNode(final DefaultMutableTreeNode node) {
    if (node instanceof PackageDependenciesNode) {
      return ((PackageDependenciesNode)node).getPsiElement();
    }
    return super.exhumeElementFromNode(node);
  }
}
