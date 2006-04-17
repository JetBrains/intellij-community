package com.intellij.ide.errorTreeView;

import com.intellij.ide.*;
import com.intellij.ide.actions.*;
import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import com.intellij.ide.errorTreeView.impl.ErrorViewTextExporter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.MessageView;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class NewErrorTreeViewPanel extends JPanel implements DataProvider, OccurenceNavigator, ErrorTreeView {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.errorTreeView.NewErrorTreeViewPanel");
  private String myProgressText = "";
  private final boolean myCreateExitAction;
  private ErrorViewStructure myErrorViewStructure;
  private ErrorViewTreeBuilder myBuilder;

  public static interface ProcessController {
    void stopProcess();

    boolean isProcessStopped();
  }

  private ActionToolbar myLeftToolbar;
  private ActionToolbar myRightToolbar;
  private TreeExpander myTreeExpander = new MyTreeExpander();
  private ExporterToTextFile myExporterToTextFile;
  protected Project myProject;
  private String myHelpId;
  protected Tree myTree;
  private JPanel myMessagePanel;
  private ProcessController myProcessController;

  private JLabel myProgressTextLabel;
  private JLabel myProgressStatisticsLabel;
  private JPanel myProgressPanel;

  private AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private MyOccurenceNavigatorSupport myOccurenceNavigatorSupport;

  public NewErrorTreeViewPanel(Project project, String helpId) {
    this(project, helpId, true);
  }

  public NewErrorTreeViewPanel(Project project, String helpId, boolean createExitAction) {
    this(project, helpId, createExitAction, true);
  }

  public NewErrorTreeViewPanel(Project project, String helpId, boolean createExitAction, boolean createToolbar) {
    myProject = project;
    myHelpId = helpId;
    myCreateExitAction = createExitAction;
    setLayout(new BorderLayout());

    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return ErrorTreeViewConfiguration.getInstance(myProject).isAutoscrollToSource();
      }

      protected void setAutoScrollMode(boolean state) {
        ErrorTreeViewConfiguration.getInstance(myProject).setAutoscrollToSource(state);
      }
    };

    myMessagePanel = new JPanel(new BorderLayout());

    myErrorViewStructure = new ErrorViewStructure(project, canHideWarnings());
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    root.setUserObject(myErrorViewStructure.createDescriptor(myErrorViewStructure.getRootElement(), null));
    final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    myTree = new Tree(treeModel) {
      public void setRowHeight(int i) {
        super.setRowHeight(0);
        // this is needed in order to make UI calculate the height for each particular row
      }
    };
    myBuilder = new ErrorViewTreeBuilder(myTree, treeModel, myErrorViewStructure);

    myExporterToTextFile = new ErrorViewTextExporter(myErrorViewStructure);
    myOccurenceNavigatorSupport = new MyOccurenceNavigatorSupport(myTree);

    myAutoScrollToSourceHandler.install(myTree);
    TreeUtil.installActions(myTree);
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setLargeModel(true);
    myTree.setLargeModel(true);

    JScrollPane scrollPane = NewErrorTreeRenderer.install(myTree);
    myMessagePanel.add(scrollPane, BorderLayout.CENTER);

    if (createToolbar) {
      add(createToolbarPanel(), BorderLayout.WEST);
    }

    add(myMessagePanel, BorderLayout.CENTER);

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          navigateToSource(false);
        }
      }
    });

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });

    EditSourceOnDoubleClickHandler.install(myTree);
  }

  public void dispose() {
    myBuilder.dispose();
  }

  public Object getData(String dataId) {
    if (DataConstants.NAVIGATABLE.equals(dataId)) {
      final NavigatableMessageElement selectedMessageElement = getSelectedMessageElement();
      return selectedMessageElement != null? selectedMessageElement.getNavigatable() : null;
    }
    else if (DataConstants.HELP_ID.equals(dataId)) {
      return myHelpId;
    }
    else if (DataConstantsEx.TREE_EXPANDER.equals(dataId)) {
      return myTreeExpander;
    }
    else if (DataConstants.EXPORTER_TO_TEXT_FILE.equals(dataId)) {
      return myExporterToTextFile;
    }
    else if (CURRENT_EXCEPTION_DATA.equals(dataId)) {
      NavigatableMessageElement selectedMessageElement = getSelectedMessageElement();
      return selectedMessageElement != null? selectedMessageElement.getData() : null;
    }
    return null;
  }

  public void selectFirstMessage() {
    final ErrorTreeElement firstError = myErrorViewStructure.getFirstMessage(ErrorTreeElementKind.ERROR);
    if (firstError != null) {
      selectElement(firstError);
      if (shouldShowFirstErrorInEditor()) {
        navigateToSource(false);
      }
    }
    else {
      ErrorTreeElement firstWarning = myErrorViewStructure.getFirstMessage(ErrorTreeElementKind.WARNING);
      if (firstWarning != null) {
        selectElement(firstWarning);
      }
      else {
        TreeUtil.selectFirstNode(myTree);
      }
    }
  }

  private void selectElement(final ErrorTreeElement element) {
    myBuilder.updateFromRoot();
    DefaultMutableTreeNode node = myBuilder.getNodeForElement(element);
    if (node == null) {
      myBuilder.buildNodeForElement(element);
      node = myBuilder.getNodeForElement(element);
    }
    LOG.assertTrue(node != null);
    TreeNode[] pathToRoot = ((DefaultTreeModel)myTree.getModel()).getPathToRoot(node);
    TreeUtil.selectPath(myTree, new TreePath(pathToRoot));
  }

  protected boolean shouldShowFirstErrorInEditor() {
    return false;
  }
  public void clearMessages() {
    myErrorViewStructure.clear();
    myBuilder.updateTree();
  }

  public void updateTree() {
    myBuilder.updateTree();
  }
  public void addMessage(int type, String[] text, VirtualFile file, int line, int column, Object data) {
    myErrorViewStructure.addMessage(ErrorTreeElementKind.convertMessageFromCompilerErrorType(type), text, file, line, column, data);
    myBuilder.updateTree();
  }

  public void addMessage(int type, String[] text, String groupName, Navigatable navigatable, String exportTextPrefix, String rendererTextPrefix, Object data) {
    myErrorViewStructure.addNavigatableMessage(groupName, navigatable, ErrorTreeElementKind.convertMessageFromCompilerErrorType(type), text, data, exportTextPrefix, rendererTextPrefix);
    myBuilder.updateTree();
  }

  public ErrorViewStructure getErrorViewStructure() {
    return myErrorViewStructure;
  }

  public static String createExportPrefix(int line) {
    return line < 0? "" : IdeBundle.message("errortree.prefix.line", line);
  }

  public static String createRendererPrefix(int line, int column) {
    return line < 0? "" : "(" + line + ", " + column + ")";
  }

  public JComponent getComponent() {
    return this;
  }

  private NavigatableMessageElement getSelectedMessageElement() {
    final ErrorTreeElement selectedElement = getSelectedErrorTreeElement();
    return selectedElement instanceof NavigatableMessageElement? (NavigatableMessageElement)selectedElement : null;
  }

  public ErrorTreeElement getSelectedErrorTreeElement() {
    ErrorTreeNodeDescriptor treeNodeDescriptor = getSelectedNodeDescriptor();
    if (treeNodeDescriptor == null) return null;

    return treeNodeDescriptor.getElement();
  }

  public ErrorTreeNodeDescriptor getSelectedNodeDescriptor() {
    TreePath path = myTree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode lastPathNode = (DefaultMutableTreeNode)path.getLastPathComponent();
    Object userObject = lastPathNode.getUserObject();
    if (!(userObject instanceof ErrorTreeNodeDescriptor)) {
      return null;
    }
    return (ErrorTreeNodeDescriptor)userObject;
  }

  private void navigateToSource(final boolean focusEditor) {
    NavigatableMessageElement element = getSelectedMessageElement();
    if (element == null) {
      return;
    }
    final Navigatable navigatable = element.getNavigatable();
    if (navigatable.canNavigate()) {
      navigatable.navigate(focusEditor);
    }
  }

  public static String getQualifiedName(final VirtualFile file) {
    return file.getPresentableUrl();
  }

  private void popupInvoked(Component component, int x, int y) {
    final TreePath path = myTree.getLeadSelectionPath();
    if (path == null) {
      return;
    }
    DefaultActionGroup group = new DefaultActionGroup();
    if (getData(DataConstants.NAVIGATABLE) != null) {
      group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    }
    addExtraPopupMenuActions(group);

    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.COMPILER_MESSAGES_POPUP, group);
    menu.getComponent().show(component, x, y);
  }

  protected void addExtraPopupMenuActions(DefaultActionGroup group) {
  }

  public void setProcessController(ProcessController controller) {
    myProcessController = controller;
  }

  public void stopProcess() {
    myProcessController.stopProcess();
  }

  public boolean canControlProcess() {
    return myProcessController != null;
  }

  public boolean isProcessStopped() {
    return myProcessController.isProcessStopped();
  }

  public void close() {
    MessageView messageView = myProject.getComponent(MessageView.class);
    Content content = messageView.getContent(this);
    if (content != null) {
      messageView.removeContent(content);
    }
  }

  public void setProgressStatistics(final String s) {
    initProgressPanel();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myProgressStatisticsLabel.setText(s);
      }
    });
  }

  public void setProgressText(final String s) {
    initProgressPanel();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myProgressText = s;
        myProgressTextLabel.setText(myProgressText);
      }
    });
  }

  public void setFraction(final double fraction) {
    initProgressPanel();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myProgressTextLabel.setText(myProgressText + " " + (int)(fraction * 100 + 0.5) + "%");
      }
    });
  }

  private void initProgressPanel() {
    if (myProgressPanel == null) {
      myProgressPanel = new JPanel(new GridLayout(1, 2));
      myProgressStatisticsLabel = new JLabel();
      myProgressPanel.add(myProgressStatisticsLabel);
      myProgressTextLabel = new JLabel();
      myProgressPanel.add(myProgressTextLabel);
      myMessagePanel.add(myProgressPanel, BorderLayout.SOUTH);
      myMessagePanel.validate();
    }
  }

  public void collapseAll() {
    TreeUtil.collapseAll(myTree, 2);
  }


  public void expandAll() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    int row = 0;
    while (row < myTree.getRowCount()) {
      myTree.expandRow(row);
      row++;
    }

    if (selectionPaths != null) {
      // restore selection
      myTree.setSelectionPaths(selectionPaths);
    }
    if (leadSelectionPath != null) {
      // scroll to lead selection path
      myTree.scrollPathToVisible(leadSelectionPath);
    }
  }

  private JPanel createToolbarPanel() {
    AnAction closeMessageViewAction = new CloseTabToolbarAction() {
      public void actionPerformed(AnActionEvent e) {
        close();
      }
    };

    DefaultActionGroup leftUpdateableActionGroup = new DefaultActionGroup();
    leftUpdateableActionGroup.add(new StopAction());
    if (myCreateExitAction) {
      leftUpdateableActionGroup.add(closeMessageViewAction);
    }
    leftUpdateableActionGroup.add(new PreviousOccurenceToolbarAction(this));
    leftUpdateableActionGroup.add(new NextOccurenceToolbarAction(this));
    leftUpdateableActionGroup.add(new ExportToTextFileToolbarAction(myExporterToTextFile));
    leftUpdateableActionGroup.add(CommonActionsFactory.getCommonActionsFactory().createContextHelpAction(myHelpId));

    DefaultActionGroup rightUpdateableActionGroup = new DefaultActionGroup();
    fillRightToolbarGroup(rightUpdateableActionGroup);

    JPanel toolbarPanel = new JPanel(new GridLayout(1, 2));
    final ActionManager actionManager = ActionManager.getInstance();
    myLeftToolbar =
    actionManager.createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, leftUpdateableActionGroup, false);
    toolbarPanel.add(myLeftToolbar.getComponent());
    myRightToolbar =
    actionManager.createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, rightUpdateableActionGroup, false);
    toolbarPanel.add(myRightToolbar.getComponent());

    return toolbarPanel;
  }

  protected void fillRightToolbarGroup(DefaultActionGroup group) {
    group.add(new ExpandAllToolbarAction(myTreeExpander));
    group.add(new CollapseAllToolbarAction(myTreeExpander));
    if (canHideWarnings()) {
      group.add(new HideWarningsAction());
    }
    group.add(myAutoScrollToSourceHandler.createToggleAction());
  }

  public OccurenceInfo goNextOccurence() {
    return myOccurenceNavigatorSupport.goNextOccurence();
  }

  public OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigatorSupport.goPreviousOccurence();
  }

  public boolean hasNextOccurence() {
    return myOccurenceNavigatorSupport.hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return myOccurenceNavigatorSupport.hasPreviousOccurence();
  }

  public String getNextOccurenceActionName() {
    return myOccurenceNavigatorSupport.getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigatorSupport.getPreviousOccurenceActionName();
  }

  private class StopAction extends AnAction {
    public StopAction() {
      super(IdeBundle.message("action.stop"), null, IconLoader.getIcon("/actions/suspend.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      if (canControlProcess()) {
        stopProcess();
      }
      myLeftToolbar.updateActionsImmediately();
      myRightToolbar.updateActionsImmediately();
    }

    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(canControlProcess() && !isProcessStopped());
    }
  }

  protected boolean canHideWarnings(){
    return true;
  }

  private class HideWarningsAction extends ToggleAction {
    public HideWarningsAction() {
      super(IdeBundle.message("action.hide.warnings"), null, IconLoader.getIcon("/compiler/hideWarnings.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      final ErrorTreeViewConfiguration configuration = ErrorTreeViewConfiguration.getInstance(myProject);
      final boolean hideWarnings = configuration.isHideWarnings();
      if (hideWarnings != flag) {
        configuration.setHideWarnings(flag);
        myBuilder.updateTree();
      }
    }
  }

  private class MyTreeExpander implements TreeExpander {
    public void expandAll() {
      NewErrorTreeViewPanel.this.expandAll();
    }

    public boolean canExpand() {
      return true;
    }

    public void collapseAll() {
      NewErrorTreeViewPanel.this.collapseAll();
    }

    public boolean canCollapse() {
      return true;
    }
  }

  private static class MyOccurenceNavigatorSupport extends OccurenceNavigatorSupport {
    public MyOccurenceNavigatorSupport(final Tree tree) {
      super(tree);
    }

    protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ErrorTreeNodeDescriptor)) {
        return null;
      }
      final ErrorTreeNodeDescriptor descriptor = (ErrorTreeNodeDescriptor)userObject;
      final ErrorTreeElement element = descriptor.getElement();
      if (element instanceof NavigatableMessageElement) {
        return ((NavigatableMessageElement)element).getNavigatable();
      }
      return null;
    }

    public String getNextOccurenceActionName() {
      return IdeBundle.message("action.next.message");
    }

    public String getPreviousOccurenceActionName() {
      return IdeBundle.message("action.previous.message");
    }
  }
}