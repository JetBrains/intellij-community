package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.Form;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public class FavoritesTreeViewPanel extends JPanel implements DataProvider {
  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon HIDE_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/hideEmptyPackages.png");


  private FavoritesTreeStructure myFavoritesTreeStructure;
  private FavoritesViewTreeBuilder myBuilder;
  /*private Splitter mySplitter;
  private JPanel myStructurePanel;
  private MyStructureViewWrapper myStructureViewWrapper;
  */
  private CopyPasteManagerEx.CopyPasteDelegator myCopyPasteDelegator;

  public void removeFromFavorites(final AbstractTreeNode element) {
    myFavoritesTreeStructure.removeFromFavorites(element);
    myBuilder.updateTree();
  }

  protected Project myProject;
  private String myHelpId;
  protected Tree myTree;

  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();
  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();
  private final FavoritesTreeViewConfiguration myFavoritesConfiguration;

  private AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private String myName;
  public FavoritesTreeViewPanel(Project project, String helpId, String name) {
    myProject = project;
    myHelpId = helpId;
    myName = name;
    setLayout(new BorderLayout());

    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler(myProject) {
      protected boolean isAutoScrollMode() {
        return myFavoritesConfiguration.IS_AUTOSCROLL_TO_SOURCE;
      }

      protected void setAutoScrollMode(boolean state) {
        myFavoritesConfiguration.IS_AUTOSCROLL_TO_SOURCE = state;
      }
    };

    //JPanel myCenterPanel = new JPanel(new BorderLayout());

    myFavoritesTreeStructure = new FavoritesTreeStructure(project);
    myFavoritesConfiguration = myFavoritesTreeStructure.getFavoritesConfiguration();
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    root.setUserObject(myFavoritesTreeStructure.getRootElement());
    final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    myTree = new Tree(treeModel) {
      public void setRowHeight(int i) {
        super.setRowHeight(0);
      }
    };
    myBuilder = new FavoritesViewTreeBuilder(myProject, myTree, treeModel, myFavoritesTreeStructure);

    myAutoScrollToSourceHandler.install(myTree);
    TreeUtil.installActions(myTree);
    myTree.putClientProperty("JTree.lineStyle", "Angled");
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setLargeModel(true);
    new TreeSpeedSearch(myTree);
    myTree.setCellRenderer(new NodeRenderer() {
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row,
                                    hasFocus);
        if (value instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
          //only favorites roots to explain
          if (node.getParent() == null || node.getParent().getParent() != null){
            return;
          }
          Object userObject = node.getUserObject();

          if (userObject instanceof FavoritesTreeNodeDescriptor) {
            final FavoritesTreeNodeDescriptor favoritesTreeNodeDescriptor = ((FavoritesTreeNodeDescriptor)userObject);
            AbstractTreeNode treeNode = favoritesTreeNodeDescriptor.getElement();
            String locationString = treeNode.getPresentation().getLocationString();
            if (locationString != null && locationString.length() > 0) {
              append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
            } else {
              final String location = favoritesTreeNodeDescriptor.getLocation();
              if (location != null && location.length() > 0){
                append(" (" + location + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
              }
            }
          }
        }
      }
    });
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    PopupHandler.installPopupHandler(myTree, (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_FAVORITES_VIEW_POPUP),
                                     ActionPlaces.FAVORITES_VIEW_POPUP, ActionManager.getInstance());
    /* mySplitter = new Splitter(true);
     mySplitter.setHonorComponentsMinimumSize(true);
     mySplitter.setFirstComponent(myCenterPanel);
     myStructurePanel = new JPanel(new BorderLayout());
     myStructureViewWrapper = new MyStructureViewWrapper();
     myStructureViewWrapper.setFileEditor(null);
     myStructurePanel.add(myStructureViewWrapper.getComponent());
     mySplitter.setSecondComponent(myStructurePanel);

    */ add(scrollPane,
                                                                                                                                                                                                                                                                                                                                                                                                                                                BorderLayout.CENTER);
    add(createActionsToolbar(), BorderLayout.NORTH);

    EditSourceOnDoubleClickHandler.install(myTree);
    myCopyPasteDelegator = new CopyPasteManagerEx.CopyPasteDelegator(myProject, this) {
      protected PsiElement[] getSelectedElements() {
        return getSelectedPsiElements();
      }
    };
  }

  public void selectElement(final Object selector, final VirtualFile file){
    myBuilder.select(selector, file, true, myBuilder);
  }
  /*public void selectElement(final AbstractTreeNode element) {
    myBuilder.updateFromRoot();
    DefaultMutableTreeNode node = myBuilder.getNodeForElement(element);
    if (node == null) {
      myBuilder.buildNodeForElement(element);
      node = myBuilder.getNodeForElement(element);
    }
    TreeNode[] pathToRoot = ((DefaultTreeModel)myTree.getModel()).getPathToRoot(node);
    TreeUtil.selectPath(myTree, new TreePath(pathToRoot));
  }*/

  public Tree getTree() {
    return myTree;
  }

  public String getName() {
    return myName;
  }

  private PsiElement[] getSelectedPsiElements() {
    final Object[] elements = getSelectedNodeElements();
    if (elements == null) {
      return null;
    }
    ArrayList<PsiElement> result = new ArrayList<PsiElement>();
    for (int i = 0; i < elements.length; i++) {
      Object element = elements[i];
      if (element instanceof PsiElement) {
        result.add((PsiElement)element);
      }
    }
    return result.isEmpty() ? null : result.toArray(new PsiElement[result.size()]);
  }

  public FavoritesTreeStructure getFavoritesTreeStructure() {
    return myFavoritesTreeStructure;
  }

  public Object getData(String dataId) {
    if (DataConstants.PROJECT.equals(dataId)) {
      return myProject;
    }
    if (DataConstants.NAVIGATABLE.equals(dataId)) {
      final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = getSelectedNodeDescriptors();
      return selectedNodeDescriptors != null && selectedNodeDescriptors.length == 1 ? selectedNodeDescriptors[0].getElement() : null;
    }
    if (DataConstantsEx.CUT_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCutProvider();
    }
    if (DataConstantsEx.COPY_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCopyProvider();
    }
    if (DataConstantsEx.PASTE_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getPasteProvider();
    }
    else if (DataConstants.HELP_ID.equals(dataId)) {
      return myHelpId;
    }
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      final PsiElement psiElement;
      Object[] elements = getSelectedNodeElements();
      if (elements == null || elements.length != 1) {
        return null;
      }
      if (elements[0] instanceof PsiElement) {
        psiElement = (PsiElement)elements[0];
      }
      else if (elements[0] instanceof PackageElement) {
        psiElement = ((PackageElement)elements[0]).getPackage();
      }
      else {
        psiElement = null;
      }
      return psiElement != null && psiElement.isValid() ? psiElement : null;
    }
    if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
      final PsiElement[] elements = getSelectedPsiElements();
      if (elements == null) {
        return null;
      }
      ArrayList<PsiElement> result = new ArrayList<PsiElement>();
      for (int i = 0; i < elements.length; i++) {
        PsiElement element = elements[i];
        if (element.isValid()) {
          result.add(element);
        }
      }
      return result.isEmpty() ? null : result.toArray(new PsiElement[result.size()]);
    }
    if (DataConstantsEx.TARGET_PSI_ELEMENT.equals(dataId)) {
      return null;
    }

    if (DataConstantsEx.MODULE_CONTEXT.equals(dataId)) {
      Module[] selected = getSelectedModules();
      return selected != null && selected.length == 1 ? selected[0] : null;
    }
    if (DataConstantsEx.MODULE_CONTEXT_ARRAY.equals(dataId)) {
      return getSelectedModules();
    }

    if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      final Object[] elements = getSelectedNodeElements();
      return elements != null && elements.length >= 1 && elements[0] instanceof Module
        ? (DeleteProvider)myDeleteModuleProvider
        : myDeletePSIElementProvider;

    }
    if (DataConstantsEx.MODULE_GROUP_ARRAY.equals(dataId)) {
      final List<ModuleGroup> selectedElements = getSelectedElements(ModuleGroup.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new ModuleGroup[selectedElements.size()]);
    }
    if (DataConstantsEx.GUI_DESIGNER_FORM_ARRAY.equals(dataId)) {
      final List<Form> selectedElements = getSelectedElements(Form.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new Form[selectedElements.size()]);
    }
    if (DataConstantsEx.LIBRARY_GROUP_ARRAY.equals(dataId)) {
      final List<LibraryGroupElement> selectedElements = getSelectedElements(LibraryGroupElement.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new LibraryGroupElement[selectedElements.size()]);
    }
    if (DataConstantsEx.NAMED_LIBRARY_ARRAY.equals(dataId)) {
      final List<NamedLibraryElement> selectedElements = getSelectedElements(NamedLibraryElement.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new NamedLibraryElement[selectedElements.size()]);
    }
    return null;
  }

  private <T>List<T> getSelectedElements(Class<T> klass) {
    final Object[] elements = getSelectedNodeElements();
    ArrayList<T> result = new ArrayList<T>();
    for (int i = 0; i < elements.length; i++) {
      Object element = elements[i];
      if (klass.isAssignableFrom(element.getClass())) {
        result.add((T)element);
      }
    }
    return result;
  }

  private Module[] getSelectedModules() {
    final Object[] elements = getSelectedNodeElements();
    if (elements == null) {
      return null;
    }
    ArrayList<Module> result = new ArrayList<Module>();
    for (int i = 0; i < elements.length; i++) {
      Object element = elements[i];
      if (element instanceof Module) {
        result.add((Module)element);
      }
      else if (element instanceof ModuleGroup) {
        result.addAll(Arrays.asList(((ModuleGroup)element).modulesInGroup(myProject, true)));
      }
    }

    return result.isEmpty() ? null : result.toArray(new Module[result.size()]);
  }

  private Object[] getSelectedNodeElements() {
    final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = getSelectedNodeDescriptors();
    if (selectedNodeDescriptors == null) {
      return null;
    }
    ArrayList result = new ArrayList();
    for (int i = 0; i < selectedNodeDescriptors.length; i++) {
      FavoritesTreeNodeDescriptor selectedNodeDescriptor = selectedNodeDescriptors[i];
      if (selectedNodeDescriptor != null) {
        result.add(selectedNodeDescriptor.getElement().getValue());
      }
    }
    return result.toArray(new Object[result.size()]);
  }

  private JComponent createActionsToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.removeAll();
    group.add(new ToggleAction("Flatten Packages", "Flatten Packages", IconLoader.getIcon("/objectBrowser/flattenPackages.png")) {
      public boolean isSelected(AnActionEvent e) {
        return myFavoritesConfiguration.IS_FLATTEN_PACKAGES;
      }

      public void setSelected(AnActionEvent event, boolean flag) {
        final SelectionInfo selectionInfo = new SelectionInfo();
        myFavoritesConfiguration.IS_FLATTEN_PACKAGES = flag;
        myBuilder.updateTree();
        selectionInfo.apply();
      }
    });

    group.add(new ToggleAction("") {
      public boolean isSelected(AnActionEvent e) {
        return myFavoritesConfiguration.IS_HIDE_EMPTY_MIDDLE_PACKAGES;
      }

      public void setSelected(AnActionEvent event, boolean flag) {
        final SelectionInfo selectionInfo = new SelectionInfo();
        myFavoritesConfiguration.IS_HIDE_EMPTY_MIDDLE_PACKAGES = flag;
        myBuilder.updateTree();
        selectionInfo.apply();
      }

      public void update(AnActionEvent e) {
        super.update(e);
        final Presentation presentation = e.getPresentation();
        if (myFavoritesConfiguration.IS_FLATTEN_PACKAGES) {
          presentation.setText("Hide Empty Middle Packages");
          presentation.setDescription("Show/Hide Empty Middle Packages");
          presentation.setIcon(HIDE_EMPTY_MIDDLE_PACKAGES_ICON);
        }
        else {
          presentation.setText("Compact Empty Middle Packages");
          presentation.setDescription("Show/Compact Empty Middle Packages");
          presentation.setIcon(COMPACT_EMPTY_MIDDLE_PACKAGES_ICON);
        }
      }
    });
    group.add(new ToggleAction("Abbreviate Qualified Package Names", "Abbreviate Qualified Package Names",
                               IconLoader.getIcon("/objectBrowser/abbreviatePackageNames.png")) {
      public boolean isSelected(AnActionEvent e) {
        return myFavoritesConfiguration.IS_ABBREVIATION_PACKAGE_NAMES;
      }

      public void setSelected(AnActionEvent e, boolean state) {
        myFavoritesConfiguration.IS_ABBREVIATION_PACKAGE_NAMES = state;
        myBuilder.updateTree();
      }

      public void update(final AnActionEvent e) {
        e.getPresentation().setEnabled(myFavoritesConfiguration.IS_FLATTEN_PACKAGES);
      }
    });
    group.add(new ToggleAction("Show Members", "Show/Hide Members", IconLoader.getIcon("/objectBrowser/showMembers.png")) {
      public boolean isSelected(AnActionEvent e) {
        return myFavoritesConfiguration.IS_SHOW_MEMBERS;
      }

      public void setSelected(AnActionEvent e, boolean state) {
        SelectionInfo selectionInfo = new SelectionInfo();
        myFavoritesConfiguration.IS_SHOW_MEMBERS = state;
        myBuilder.updateTree();
        selectionInfo.apply();
      }
    });
    group.add(myAutoScrollToSourceHandler.createToggleAction());
    //group.add(new ShowStructureAction());
    group.add(ActionManager.getInstance().getAction(IdeActions.REMOVE_FROM_FAVORITES));
    group.add(ActionManager.getInstance().getAction(IdeActions.ADD_NEW_FAVORITES_LIST));
    group.add(ActionManager.getInstance().getAction(IdeActions.REMOVE_FAVORITES_LIST));
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.FAVORITES_VIEW_TOOLBAR, group, true).getComponent();
  }


  public void addToFavorites(AbstractTreeNode node) {
    myFavoritesTreeStructure.addToFavorites(node);
    myBuilder.updateTree();
  }

  public FavoritesTreeNodeDescriptor[] getSelectedNodeDescriptors() {
    TreePath[] path = myTree.getSelectionPaths();
    if (path == null) {
      return null;
    }
    ArrayList<FavoritesTreeNodeDescriptor> result = new ArrayList<FavoritesTreeNodeDescriptor>();
    for (int i = 0; i < path.length; i++) {
      TreePath treePath = path[i];
      DefaultMutableTreeNode lastPathNode = (DefaultMutableTreeNode)treePath.getLastPathComponent();
      Object userObject = lastPathNode.getUserObject();
      if (!(userObject instanceof FavoritesTreeNodeDescriptor)) {
        continue;
      }
      FavoritesTreeNodeDescriptor treeNodeDescriptor = (FavoritesTreeNodeDescriptor)userObject;
      result.add(treeNodeDescriptor);
    }
    return result.isEmpty() ? null : result.toArray(new FavoritesTreeNodeDescriptor[result.size()]);
  }

  public static String getQualifiedName(final VirtualFile file) {
    return file.getPresentableUrl();
  }

  public FavoritesViewTreeBuilder getBuilder() {
    return myBuilder;
  }


  private class SelectionInfo {
    private final Object[] myElements;

    public SelectionInfo() {
      List selectedElements = Collections.EMPTY_LIST;
      final TreePath[] selectionPaths = myTree.getSelectionPaths();
      if (selectionPaths != null) {
        selectedElements = new ArrayList();
        for (int idx = 0; idx < selectionPaths.length; idx++) {
          TreePath path = selectionPaths[idx];
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
          final NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
          selectedElements.add(descriptor.getElement());
        }
      }
      myElements = selectedElements.toArray(new Object[selectedElements.size()]);
    }

    public void apply() {
      final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
      final List<TreePath> paths = new ArrayList<TreePath>(myElements.length);
      for (int idx = 0; idx < myElements.length; idx++) {
        final AbstractTreeNode element = (AbstractTreeNode)myElements[idx];
        DefaultMutableTreeNode node = myBuilder.getNodeForElement(element);
        if (node == null) {
          myBuilder.buildNodeForElement(element);
          node = myBuilder.getNodeForElement(element);
        }
        if (node != null) {
          paths.add(new TreePath(treeModel.getPathToRoot(node)));
        }
      }
      if (paths.size() > 0) {
        myTree.setSelectionPaths(paths.toArray(new TreePath[paths.size()]));
      }
    }
  }

  /*private final class ShowStructureAction extends ToggleAction {
    ShowStructureAction() {
      super("Show Structure", "Show structure view", IconLoader.getIcon("/objectBrowser/showStructure.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return FavoritesTreeViewConfiguration.getInstance(myProject).IS_STRUCTURE_VIEW;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      showOrHideStructureView(flag);
    }
  }

  private void showOrHideStructureView(boolean toShow) {
    boolean hadFocus = IJSwingUtilities.hasFocus2(getComponent());

    myStructurePanel.setVisible(toShow);

    FavoritesTreeViewConfiguration.getInstance(myProject).IS_STRUCTURE_VIEW = toShow;

    if (hadFocus) {
      FavoritesTreeViewPanel.this.requestFocus();
    }

    if (toShow) {
      VirtualFile[] files = FileEditorManager.getInstance(myProject).getSelectedFiles();
      PsiFile psiFile = files.length != 0 ? PsiManager.getInstance(myProject).findFile(files[0]) : null;
      myStructureViewWrapper.setFileEditor(null);
    }
  }

  private final class MyStructureViewWrapper extends StructureViewWrapper {
    MyStructureViewWrapper() {
      super(myProject);
    }

    protected boolean isStructureViewShowing() {
      return myStructurePanel.isVisible();
    }
  }
*/
  private final class MyDeletePSIElementProvider implements DeleteProvider {
    public boolean canDeleteElement(DataContext dataContext) {
      final PsiElement[] elements = getElementsToDelete();
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    public void deleteElement(DataContext dataContext) {
      List<PsiElement> allElements = Arrays.asList(getElementsToDelete());
      List<PsiElement> validElements = new ArrayList<PsiElement>();
      for (Iterator<PsiElement> iterator = allElements.iterator(); iterator.hasNext();) {
        PsiElement psiElement = iterator.next();
        if (psiElement != null && psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = validElements.toArray(new PsiElement[validElements.size()]);

      LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, "Deleting");
      try {
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        LvcsIntegration.checkinFilesAfterRefactoring(myProject, action);
      }
    }

    private PsiElement[] getElementsToDelete() {
      ArrayList<PsiElement> result = new ArrayList<PsiElement>();
      Object[] elements = getSelectedNodeElements();
      for (int idx = 0; elements != null && idx < elements.length; idx++) {
        if (elements[idx] instanceof PsiElement) {
          final PsiElement element = (PsiElement)elements[idx];
          result.add(element);
          if (element instanceof PsiDirectory) {
            final VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
            final String path = virtualFile.getPath();
            if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) { // if is jar-file root
              final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(
                path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length()));
              if (vFile != null) {
                final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
                if (psiFile != null) {
                  elements[idx] = psiFile;
                }
              }
            }
          }
        }
      }

      return result.toArray(new PsiElement[result.size()]);
    }

  }
}