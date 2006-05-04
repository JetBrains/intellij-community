package com.intellij.ide.scopeView;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ui.*;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 25-Jan-2006
 */
public class ScopeTreeViewPanel extends JPanel implements JDOMExternalizable, DataProvider {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.scopeView.ScopeTreeViewPanel");
  private IdeView myIdeView = new  MyIdeView();
  private MyPsiTreeChangeAdapter myPsiTreeChangeAdapter = new MyPsiTreeChangeAdapter();
  private ModuleRootListener myModuleRootListener = new MyModuleRootListener();

  private JTree myTree = new JTree();
  private final Project myProject;
  private TreeModelBuilder myBuilder;
  public String CURRENT_SCOPE_NAME;

  private boolean myInitialized = false;

  private TreeExpansionMonitor myTreeExpansionMonitor;
  private CopyPasteManagerEx.CopyPasteDelegator myCopyPasteDelegator;
  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();
  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();
  private final DependencyValidationManager myDependencyValidationManager;
  private WolfTheProblemSolver.ProblemListener myProblemListener = new MyProblemListener();

  public ScopeTreeViewPanel(final Project project) {
    super(new BorderLayout());
    myProject = project;
    initTree();

    add(new JScrollPane(myTree), BorderLayout.CENTER);
    myDependencyValidationManager = DependencyValidationManager.getInstance(myProject);
  }

  public void initListeners(){
    myInitialized = true;
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeAdapter);
    ProjectRootManager.getInstance(myProject).addModuleRootListener(myModuleRootListener);
    WolfTheProblemSolver.getInstance(myProject).addProblemListener(myProblemListener);
  }

  public void dispose(){
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeAdapter);
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myModuleRootListener);
    WolfTheProblemSolver.getInstance(myProject).removeProblemListener(myProblemListener);
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  public PackageDependenciesNode findNode(PsiFile file) {
    return myBuilder.findNode(file);
  }

  public void selectScope(final NamedScope scope) {
    refreshScope(scope, myDependencyValidationManager, true);
    if (scope != myDependencyValidationManager.getProjectScope()) {
      CURRENT_SCOPE_NAME = scope.getName();
    }
  }

  public void selectCurrentScope() {
    final NamedScope scope = getCurrentScope();
    refreshScope(scope, myDependencyValidationManager, true);
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
    EditSourceOnDoubleClickHandler.install(myTree);
    myCopyPasteDelegator = new CopyPasteManagerEx.CopyPasteDelegator(myProject, this) {
      protected PsiElement[] getSelectedElements() {
        return getSelectedPsiElements();
      }
    };
    myTreeExpansionMonitor = TreeExpansionMonitor.install(myTree, myProject);
    myTree.addTreeWillExpandListener(new ScopeTreeViewExpander(myTree, myProject));
  }

  private PsiElement[] getSelectedPsiElements() {
    final TreePath[] treePaths = myTree.getSelectionPaths();
    if (treePaths != null){
      Set<PsiElement> result = new HashSet<PsiElement>();
      for (TreePath path : treePaths) {
        PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
        final PsiElement psiElement = node.getPsiElement();
        if (psiElement != null && psiElement.isValid()){
          result.add(psiElement);
        }
      }
      return result.toArray(new PsiElement[result.size()]);
    }
    return PsiElement.EMPTY_ARRAY;
  }

  private void refreshScope(NamedScope scope, final NamedScopesHolder holder, boolean showProgress) {
    myTreeExpansionMonitor.freeze();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTree.getModel().getRoot();
    root.removeAllChildren();
    if (scope == null || scope.getValue() == null) { //was deleted
      scope = DependencyValidationManager.getInstance(myProject).getProjectScope();
    }
    final PackageSet packageSet = scope.getValue();
    final DependenciesPanel.DependencyPanelSettings settings = new DependenciesPanel.DependencyPanelSettings();
    settings.UI_FILTER_LEGALS = true;
    settings.UI_GROUP_BY_SCOPE_TYPE = false;
    settings.UI_SHOW_FILES = true;
    settings.UI_GROUP_BY_FILES = true;
    final ProjectView projectView = ProjectView.getInstance(myProject);
    settings.UI_FLATTEN_PACKAGES = projectView.isFlattenPackages(ScopeViewPane.ID);
    settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = projectView.isHideEmptyMiddlePackages(ScopeViewPane.ID);
    myBuilder = new TreeModelBuilder(myProject, false, new TreeModelBuilder.Marker() {
      public boolean isMarked(PsiFile file) {
        return packageSet.contains(file, holder);
      }
    }, settings);
    final TreeModelBuilder.TreeModel treeModel = myBuilder.build(myProject, showProgress);
    final DefaultMutableTreeNode scopeRootNode = (DefaultMutableTreeNode)treeModel.getRoot();
    for(int i = scopeRootNode.getChildCount() - 1; i >= 0; i--){
      root.add((MutableTreeNode)scopeRootNode.getChildAt(i));
    }
    ((DefaultTreeModel)myTree.getModel()).reload();
    myTreeExpansionMonitor.restore();
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  private NamedScope getCurrentScope() {
    return CURRENT_SCOPE_NAME == null ? myDependencyValidationManager.getProjectScope() : myDependencyValidationManager.getScope(CURRENT_SCOPE_NAME);
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
      if (dataId.equals(DataConstants.PSI_ELEMENT_ARRAY)) {
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
    if (dataId.equals(DataConstants.IDE_VIEW)){
      return myIdeView;
    }
    if (DataConstants.CUT_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCutProvider();
    }
    if (DataConstants.COPY_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCopyProvider();
    }
    if (DataConstants.PASTE_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getPasteProvider();
    }
    if (DataConstants.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      if (getSelectedModules() != null){
        return myDeleteModuleProvider;
      }
      if (getSelectedPsiElements() != null){
        return myDeletePSIElementProvider;
      }
    }
    return null;
  }

  private Module[] getSelectedModules(){
    final TreePath[] treePaths = myTree.getSelectionPaths();
    if (treePaths != null){
      Set<Module> result = new HashSet<Module>();
      for (TreePath path : treePaths) {
        PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
        if (node instanceof ModuleNode){
          result.add(((ModuleNode)node).getModule());
        } else if (node instanceof ModuleGroupNode){
          final ModuleGroupNode groupNode = (ModuleGroupNode)node;
          final ModuleGroup moduleGroup = groupNode.getModuleGroup();
          result.addAll(Arrays.asList(moduleGroup.modulesInGroup(myProject, true)));
        }
      }
      return result.isEmpty() ? null : result.toArray(new Module[result.size()]);
    }
    return null;
  }


  private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof PackageDependenciesNode) {
        PackageDependenciesNode node = (PackageDependenciesNode)value;
        if (expanded) {
          setIcon(node.getOpenIcon());
        }
        else {
          setIcon(node.getClosedIcon());
        }
        final SimpleTextAttributes regularAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        TextAttributes textAttributes = null;
        final String text = node.toString();
        if (text != null) {
          final PsiElement psiElement = node.getPsiElement();
          if (psiElement instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)psiElement).isDeprecated()){
            textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.DEPRECATED_ATTRIBUTES).clone();
          }
          if (textAttributes == null){
            textAttributes = regularAttributes.toTextAttributes();
          }
          textAttributes.setForegroundColor(node.getStatus().getColor());
          append(text, SimpleTextAttributes.fromTextAttributes(textAttributes));
        }
      }
    }
  }

  private class MyPsiTreeChangeAdapter extends PsiTreeChangeAdapter {
    public void beforeChildAddition(final PsiTreeChangeEvent event) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          final PsiElement element = event.getParent();
          myTreeExpansionMonitor.freeze();
          if (element instanceof PsiDirectory || element instanceof PsiPackage) {
            final PsiElement child = event.getChild();
            if (child instanceof PsiFile) {
              final DefaultMutableTreeNode rootToReload = myBuilder.addFileNode((PsiFile)child);
              final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
              if (rootToReload != null) {
                TreeUtil.sort(rootToReload, new DependencyNodeComparator());
                treeModel.reload(rootToReload);
                collapseExpand(rootToReload);
              } else {
                TreeUtil.sort(treeModel, new DependencyNodeComparator());
                treeModel.reload();
                selectCurrentScope();
              }
            }
          }
          myTreeExpansionMonitor.restore();
        }
      }, ModalityState.NON_MMODAL);
    }

    public void beforeChildRemoval(final PsiTreeChangeEvent event) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          final PsiElement child = event.getChild();
          final PsiElement parent = event.getParent();
          myTreeExpansionMonitor.freeze();
          if (parent instanceof PsiDirectory && (child instanceof PsiFile || child instanceof PsiDirectory)) {
            final DefaultMutableTreeNode node = myBuilder.removeNode(child, (PsiDirectory)parent);
            if (node != null) {
              ((DefaultTreeModel)myTree.getModel()).reload(node);
            }
            collapseExpand(node);
          }
          myTreeExpansionMonitor.restore();
        }
      }, ModalityState.NON_MMODAL);
    }

    public void beforeChildMovement(final PsiTreeChangeEvent event) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          final PsiElement oldParent = event.getOldParent();
          final PsiElement newParent = event.getNewParent();
          PsiElement child = event.getChild();
          myTreeExpansionMonitor.freeze();
          if (oldParent instanceof PsiDirectory && newParent instanceof PsiDirectory) {
            if (child instanceof PsiFile) {
              collapseExpand(myBuilder.removeNode(child, (PsiDirectory)oldParent));
              collapseExpand(myBuilder.addFileNode((PsiFile)child));
            }
          }
          myTreeExpansionMonitor.restore();
        }
      }, ModalityState.NON_MMODAL);
    }

    public void beforeChildrenChange(final PsiTreeChangeEvent event) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          final PsiElement parent = event.getParent();
          PsiJavaFile file = PsiTreeUtil.getParentOfType(parent, PsiJavaFile.class, false);
          myTreeExpansionMonitor.freeze();
          if (file != null) {
            collapseExpand(myBuilder.getFileParentNode(file));
          }
          myTreeExpansionMonitor.restore();
        }
      }, ModalityState.NON_MMODAL);
    }

  }

  private void collapseExpand(DefaultMutableTreeNode node){
    if (node == null) return;
    TreePath path = new TreePath(node.getPath());
    if (!myTree.isCollapsed(path)){
      myTree.collapsePath(path);
      myTree.expandPath(path);
      TreeUtil.sort(node, new DependencyNodeComparator());
    }
  }


  private class MyModuleRootListener implements ModuleRootListener {
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    public void rootsChanged(ModuleRootEvent event) {
      final DependencyValidationManager holder = DependencyValidationManager.getInstance(myProject);
      refreshScope(CURRENT_SCOPE_NAME != null ? holder.getScope(CURRENT_SCOPE_NAME) : holder.getProjectScope(), holder, false);
    }
  }

  private class MyIdeView implements IdeView {
    public void selectElement(final PsiElement element) {
      if (element != null) {
        final boolean isDirectory = element instanceof PsiDirectory;
        if (!isDirectory) {
          Editor editor = EditorHelper.openInEditor(element);
          if (editor != null) {
            ToolWindowManager.getInstance(myProject).activateEditorComponent();
          }
        }
      }
    }

    private PsiDirectory getDirectory() {
      final TreePath[] selectedPaths = myTree.getSelectionPaths();
      if (selectedPaths != null) {
        if (selectedPaths.length != 1) return null;
        TreePath path = selectedPaths[0];
        final PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
        if (node instanceof DirectoryNode) {
          DirectoryNode directoryNode = (DirectoryNode)node;
          while (directoryNode.getCompactedDirNode() != null){
            directoryNode = directoryNode.getCompactedDirNode();
          }
          return (PsiDirectory)directoryNode.getPsiElement();
        }
        else if (node instanceof ClassNode) {
          final PsiElement psiClass = node.getPsiElement();
          LOG.assertTrue(psiClass != null);
          final PsiFile psiFile = psiClass.getContainingFile();
          LOG.assertTrue(psiFile != null);
          return psiFile.getContainingDirectory();
        }
        else if (node instanceof FileNode) {
          final PsiFile psiFile = (PsiFile)node.getPsiElement();
          LOG.assertTrue(psiFile != null);
          return psiFile.getContainingDirectory();
        }
      }
      return null;
    }

    public PsiDirectory[] getDirectories() {
      PsiDirectory directory = getDirectory();
      return directory == null ? PsiDirectory.EMPTY_ARRAY : new PsiDirectory[]{directory};
    }

    @Nullable
    public PsiDirectory getOrChooseDirectory() {
      return PackageUtil.getOrChooseDirectory(this);
    }
  }

  private final class MyDeletePSIElementProvider implements DeleteProvider {
    public boolean canDeleteElement(DataContext dataContext) {
      final PsiElement[] elements = getSelectedPsiElements();
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    public void deleteElement(DataContext dataContext) {
      List<PsiElement> allElements = Arrays.asList(getSelectedPsiElements());
      ArrayList<PsiElement> validElements = new ArrayList<PsiElement>();
      for (PsiElement psiElement : allElements) {
        if (psiElement != null && psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = validElements.toArray(new PsiElement[validElements.size()]);

      LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, IdeBundle.message("progress.deleting"));
      try {
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        LvcsIntegration.checkinFilesAfterRefactoring(myProject, action);
      }
    }
  }

  public JTree getTree() {
    return myTree;
  }

  private class MyProblemListener implements WolfTheProblemSolver.ProblemListener {
    public void problemsChanged(final Collection<VirtualFile> added, final Collection<VirtualFile> removed) {
      AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getCurrentProjectViewPane();
      if (pane == null
          || !ScopeViewPane.ID.equals(pane.getId())
          || !DependencyValidationManager.getInstance(myProject).getProblemsScope().getName().equals(pane.getSubId())) {
        return;
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          Set<VirtualFile> filesToRefresh = new THashSet<VirtualFile>(added);
          filesToRefresh.addAll(removed);
          myTreeExpansionMonitor.freeze();

          final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
          TreeUtil.sort(treeModel, new DependencyNodeComparator());
          treeModel.reload();
          selectCurrentScope();

          //for (VirtualFile virtualFile : filesToRefresh) {
          //  PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
          //  if (psiFile == null) continue;
          //  DefaultMutableTreeNode rootToReload = added.contains(virtualFile) ?
          //                                        myBuilder.addFileNode(psiFile) :
          //                                        myBuilder.removeNode(psiFile, psiFile.getContainingDirectory());
          //  if (rootToReload != null) {
          //    TreeUtil.sort(rootToReload, new DependencyNodeComparator());
          //    treeModel.reload(rootToReload);
          //    collapseExpand(rootToReload);
          //  }
          //  else {
          //    TreeUtil.sort(treeModel, new DependencyNodeComparator());
          //    treeModel.reload();
          //    selectCurrentScope();
          //  }
          //}
          myTreeExpansionMonitor.restore();
        }
      }, ModalityState.NON_MMODAL);

    }
  }
}
