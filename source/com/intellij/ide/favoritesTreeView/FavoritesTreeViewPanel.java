package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.*;
import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.ClassSmartPointerNode;
import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.FieldSmartPointerNode;
import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.MethodSmartPointerNode;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.ui.customization.CustomizableActionsSchemas;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.Icons;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import org.jetbrains.annotations.NonNls;

public class FavoritesTreeViewPanel extends JPanel implements DataProvider {
  @NonNls
  public static final String ABSTRACT_TREE_NODE_TRANSFERABLE = "AbstractTransferable";
  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon HIDE_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/hideEmptyPackages.png");


  private FavoritesTreeStructure myFavoritesTreeStructure;
  private FavoritesViewTreeBuilder myBuilder;
  /*private Splitter mySplitter;
  private JPanel myStructurePanel;
  private MyStructureViewWrapper myStructureViewWrapper;
  */
  private CopyPasteManagerEx.CopyPasteDelegator myCopyPasteDelegator;
  private MouseListener myTreePopupHandler;

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
  private IdeView myIdeView = new MyIdeView();

  public FavoritesTreeViewPanel(Project project, String helpId, String name) {
    super(new BorderLayout());
    myProject = project;
    myHelpId = helpId;
    myName = name;

    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
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
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setLargeModel(true);
    new TreeSpeedSearch(myTree);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeToolTipHandler.install(myTree);
    final TreeExpander treeExpander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(myTree);
        if (myTree.getLeadSelectionPath() == null) {
          TreeUtil.selectFirstNode(myTree);
        }
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 1);
        if (myTree.getLeadSelectionPath() == null) {
          TreeUtil.selectFirstNode(myTree);
        }
      }

      public boolean canCollapse() {
        return true;
      }
    };
    final CommonActionsManager actionManager = CommonActionsManager.getInstance();
    AnAction expandAllToolbarAction = actionManager.createExpandAllAction(treeExpander);
    expandAllToolbarAction.registerCustomShortcutSet(expandAllToolbarAction.getShortcutSet(), myTree);
    AnAction collapseAllToolbarAction = actionManager.createCollapseAllAction(treeExpander);
    collapseAllToolbarAction.registerCustomShortcutSet(collapseAllToolbarAction.getShortcutSet(), myTree);
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
          if (node.getParent() == null || node.getParent().getParent() != null) {
            return;
          }
          Object userObject = node.getUserObject();

          if (userObject instanceof FavoritesTreeNodeDescriptor) {
            final FavoritesTreeNodeDescriptor favoritesTreeNodeDescriptor = ((FavoritesTreeNodeDescriptor)userObject);
            AbstractTreeNode treeNode = favoritesTreeNodeDescriptor.getElement();
            String locationString = treeNode.getPresentation().getLocationString();
            if (locationString != null && locationString.length() > 0) {
              append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
            }
            else {
              final String location = favoritesTreeNodeDescriptor.getLocation();
              if (location != null && location.length() > 0) {
                append(" (" + location + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
              }
            }
          }
        }
      }
    });
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    myTreePopupHandler = PopupHandler.installPopupHandler(myTree,
                                                          (ActionGroup)CustomizableActionsSchemas.getInstance()
                                                            .getCorrectedAction(IdeActions.GROUP_FAVORITES_VIEW_POPUP),
                                                          ActionPlaces.FAVORITES_VIEW_POPUP, ActionManager.getInstance());
    /* mySplitter = new Splitter(true);
     mySplitter.setHonorComponentsMinimumSize(true);
     mySplitter.setFirstComponent(myCenterPanel);
     myStructurePanel = new JPanel(new BorderLayout());
     myStructureViewWrapper = new MyStructureViewWrapper();
     myStructureViewWrapper.setFileEditor(null);
     myStructurePanel.add(myStructureViewWrapper.getComponent());
     mySplitter.setSecondComponent(myStructurePanel);

    */
    add(scrollPane, BorderLayout.CENTER);
    add(createActionsToolbar(), BorderLayout.NORTH);

    EditSourceOnDoubleClickHandler.install(myTree);
    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!e.isPopupTrigger() && e.getClickCount() == 2) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(FavoritesTreeViewPanel.this), true);
        }
      }
    });

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(FavoritesTreeViewPanel.this), false);
        }
      }
    });
    myCopyPasteDelegator = new CopyPasteManagerEx.CopyPasteDelegator(myProject, this) {
      protected PsiElement[] getSelectedElements() {
        return getSelectedPsiElements();
      }
    };
    myTree.setTransferHandler(new TransferHandler() {
      public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        for (int i = 0; i < transferFlavors.length; i++) {
          DataFlavor transferFlavor = transferFlavors[i];
          if (transferFlavor.getHumanPresentableName().equals(ABSTRACT_TREE_NODE_TRANSFERABLE)) {
            return true;
          }
        }
        return false;
      }
    });
    new DropTarget(myTree, new MyDropTargetListener());

    //DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(myTree, DnDConstants.ACTION_COPY_OR_MOVE, new MyDragGestureListener());
  }

  public void updateTreePopoupHandler(){
    myTree.removeMouseListener(myTreePopupHandler);
    ActionGroup group = (ActionGroup)CustomizableActionsSchemas.getInstance().getCorrectedAction(IdeActions.GROUP_FAVORITES_VIEW_POPUP);
    myTreePopupHandler = PopupHandler.installPopupHandler(myTree, group, ActionPlaces.FAVORITES_VIEW_POPUP, ActionManager.getInstance());
  }

  public void selectElement(final Object selector, final VirtualFile file) {
    myBuilder.select(selector, file, true);
  }

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
      } else if (element instanceof SmartPsiElementPointer){
        result.add(((SmartPsiElementPointer)element).getElement());
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
    if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
      final List<Navigatable> selectedElements = getSelectedElements(Navigatable.class);
      return selectedElements.toArray(new Navigatable[selectedElements.size()]);
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
      Object[] elements = getSelectedNodeElements();
      if (elements == null || elements.length != 1) {
        return null;
      }
      final PsiElement psiElement;
      if (elements[0] instanceof SmartPsiElementPointer){
        psiElement = ((SmartPsiElementPointer)elements[0]).getElement();
      } else if (elements[0] instanceof PsiElement) {
        psiElement = (PsiElement)elements[0];
      }
      else if (elements[0] instanceof PackageElement) {
        psiElement = ((PackageElement)elements[0]).getPackage();
      }
      else {
        return null;
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

    if (DataConstantsEx.IDE_VIEW.equals(dataId)) {
      return myIdeView;
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
    if (elements == null){
      return result;
    }
    for (int i = 0; i < elements.length; i++) {
      Object element = elements[i];
      if (element == null) continue;
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
    ArrayList<Object> result = new ArrayList<Object>();
    for (int i = 0; i < selectedNodeDescriptors.length; i++) {
      FavoritesTreeNodeDescriptor selectedNodeDescriptor = selectedNodeDescriptors[i];
      if (selectedNodeDescriptor != null) {
        Object value = selectedNodeDescriptor.getElement().getValue();
        if (value instanceof SmartPsiElementPointer){
          value = ((SmartPsiElementPointer)value).getElement();
        }
        result.add(value);
      }
    }
    return result.toArray(new Object[result.size()]);
  }

  private JComponent createActionsToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.removeAll();
    group.add(new ToggleAction(IdeBundle.message("action.flatten.packages"),
                               IdeBundle.message("action.flatten.packages"), Icons.FLATTEN_PACKAGES_ICON) {
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
          presentation.setText(IdeBundle.message("action.hide.empty.middle.packages"));
          presentation.setDescription(IdeBundle.message("action.show.hide.empty.middle.packages"));
          presentation.setIcon(HIDE_EMPTY_MIDDLE_PACKAGES_ICON);
        }
        else {
          presentation.setText(IdeBundle.message("action.compact.empty.middle.packages"));
          presentation.setDescription(IdeBundle.message("action.show.compact.empty.middle.packages"));
          presentation.setIcon(COMPACT_EMPTY_MIDDLE_PACKAGES_ICON);
        }
      }
    });
    group.add(new ToggleAction(IdeBundle.message("action.abbreviate.qualified.package.names"),
                               IdeBundle.message("action.abbreviate.qualified.package.names"),
                               IconLoader.getIcon("/objectBrowser/abbreviatePackageNames.png")) {
      public boolean isSelected(AnActionEvent e) {
        return myFavoritesConfiguration.IS_ABBREVIATION_PACKAGE_NAMES;
      }

      public void setSelected(AnActionEvent e, boolean state) {
        myFavoritesConfiguration.IS_ABBREVIATION_PACKAGE_NAMES = state;
        myBuilder.updateTree();
      }

      public void update(final AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(myFavoritesConfiguration.IS_FLATTEN_PACKAGES);
      }
    });
    group.add(new ToggleAction(IdeBundle.message("action.show.members"),
                               IdeBundle.message("action.show.hide.members"), IconLoader.getIcon("/objectBrowser/showMembers.png")) {
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

      LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, IdeBundle.message("progress.deleting"));
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

  private final class MyIdeView implements IdeView {
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
      if (myBuilder == null) return null;
      final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = getSelectedNodeDescriptors();
      if (selectedNodeDescriptors == null || selectedNodeDescriptors.length != 1) return null;
      final FavoritesTreeNodeDescriptor currentDescriptor = selectedNodeDescriptors[0];
      if (currentDescriptor != null) {
        if (currentDescriptor.getElement() != null) {
          final AbstractTreeNode currentNode = currentDescriptor.getElement();
          if (currentNode.getValue() instanceof PsiDirectory) {
            return (PsiDirectory)currentNode.getValue();
          }
        }
        final NodeDescriptor parentDescriptor = currentDescriptor.getParentDescriptor();
        if (parentDescriptor != null) {
          final Object parentElement = parentDescriptor.getElement();
          if (parentElement instanceof AbstractTreeNode) {
            final AbstractTreeNode parentNode = ((AbstractTreeNode)parentElement);
            if (parentNode.getValue() instanceof PsiDirectory) {
              return (PsiDirectory)parentNode.getValue();
            }
          }
        }
      }

      return null;
    }

    public PsiDirectory[] getDirectories() {
      PsiDirectory directory = getDirectory();
      return directory == null ? PsiDirectory.EMPTY_ARRAY : new PsiDirectory[]{directory};
    }
  }

  //---------- DnD -------------
  private Object myDraggableObject;
  private Class<? extends AbstractTreeNode> myKlass;
  public void setDraggableObject(Class<? extends AbstractTreeNode> klass, Object draggableObject) {
    myDraggableObject = draggableObject;
    if (klass == ClassTreeNode.class){
      myKlass = ClassSmartPointerNode.class;
    } else if (klass == PsiFieldNode.class) {
      myKlass = FieldSmartPointerNode.class;
    } else if (klass == PsiMethodNode.class){
      myKlass = MethodSmartPointerNode.class;
    } else {
      myKlass = klass;
    }
  }
  private class MyDropTargetListener implements DropTargetListener {
    public void dragEnter(DropTargetDragEvent dtde) {
      DataFlavor[] flavors = dtde.getCurrentDataFlavors();
      JComponent c = (JComponent)dtde.getDropTargetContext().getComponent();
      TransferHandler importer = c.getTransferHandler();
      int dropAction = dtde.getDropAction();
      if (importer != null && importer.canImport(c, flavors)) {
        dtde.acceptDrag(dropAction);
      }
      else {
        dtde.rejectDrag();
      }
    }

    public void dragOver(DropTargetDragEvent dtde) {
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    public void drop(DropTargetDropEvent dtde) {
      if (myDraggableObject != null) {
        int dropAction = dtde.getDropAction();
        if ((dropAction & DnDConstants.ACTION_MOVE) != 0) {
          final Collection<AbstractTreeNode> children = myFavoritesTreeStructure.getFavorites();
          for (AbstractTreeNode abstractTreeNode : children) {
            Object value = abstractTreeNode.getValue();
            if (value instanceof SmartPsiElementPointer){
              value = ((SmartPsiElementPointer)value).getElement();
            }
            if (Comparing.equal(value, myDraggableObject)) {
              return;
            }
          }
          try {
            if (myKlass == FormNode.class){
              final PsiManager psiManager = PsiManager.getInstance(myProject);
              addToFavorites(FormNode.constructFormNode(psiManager, (PsiClass)myDraggableObject, myProject, myFavoritesConfiguration));
            } else {
              addToFavorites(ProjectViewNode.createTreeNode(myKlass, myProject, myDraggableObject, myFavoritesConfiguration));
            }
          }
          catch (Exception e) {
            return;
          }
          dtde.dropComplete(true);
          return;
        }
      }
      dtde.rejectDrop();
    }

    public void dragExit(DropTargetEvent dte) {
    }
  }
}