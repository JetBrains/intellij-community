package com.intellij.slicer;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Enumeration;

/**
 * @author cdr
 */
public abstract class SlicePanel extends JPanel implements TypeSafeDataProvider{
  private final SliceTreeBuilder myBuilder;
  private final JTree myTree;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
    protected boolean isAutoScrollMode() {
      return isAutoScroll();
    }

    protected void setAutoScrollMode(final boolean state) {
      setAutoScroll(state);
    }
  };
  private UsagePreviewPanel myUsagePreviewPanel;
  private final Project myProject;
  private boolean isDisposed;

  public SlicePanel(Project project, SliceUsage root) {
    super(new BorderLayout());
    myProject = project;
    myTree = createTree();
    myBuilder = new SliceTreeBuilder(myTree, project, root);
    layoutPanel();
    myBuilder.addSubtreeToUpdate((DefaultMutableTreeNode)myTree.getModel().getRoot(), new Runnable() {
      public void run() {
        treeSelectionChanged();
      }
    });
  }

  private void layoutPanel() {
    removeAll();
    if (isPreview()) {
      Splitter splitter = new Splitter(false, UsageViewSettings.getInstance().PREVIEW_USAGES_SPLITTER_PROPORTIONS);
      splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
      myUsagePreviewPanel = new UsagePreviewPanel(myProject);
      splitter.setSecondComponent(myUsagePreviewPanel);
      add(splitter, BorderLayout.CENTER);
    }
    else {
      add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    }

    add(createToolbar().getComponent(), BorderLayout.WEST);

    revalidate();
  }

  public void dispose() {
    if (myUsagePreviewPanel != null) {
      UsageViewSettings.getInstance().PREVIEW_USAGES_SPLITTER_PROPORTIONS = ((Splitter)myUsagePreviewPanel.getParent()).getProportion();
      myUsagePreviewPanel.dispose();
      myUsagePreviewPanel = null;
    }
    Disposer.dispose(myBuilder);
    
    isDisposed = true;
    ToolTipManager.sharedInstance().unregisterComponent(myTree);
  }

  private JTree createTree() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    final Tree tree = new Tree(new DefaultTreeModel(root)){
      protected void paintComponent(Graphics g) {
        Rectangle clipBounds = g.getClipBounds();
        int start = getClosestRowForLocation(clipBounds.x, clipBounds.y);
        int end = Math.min(getRowCount(), getClosestRowForLocation(clipBounds.x+clipBounds.width, clipBounds.y+clipBounds.height)+1);
        Color old = g.getColor();
        for (int i = start; i < end; i++) {
          TreePath path = getPathForRow(i);
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
          Rectangle accumRect = null;
          TreePath accumPath = null;
          while (node != null) {
            Object userObject = node.getUserObject();
            if (!(userObject instanceof SliceNode) || !((SliceNode)userObject).getValue().duplicate) break;
            accumPath = accumRect == null ? path : accumPath.getParentPath();
            accumRect = getPathBounds(accumPath).union(accumRect == null ? new Rectangle() : accumRect);
            node = (DefaultMutableTreeNode)node.getParent();
          }
          if (accumRect != null) {
            Rectangle rowRect = getRowBounds(getRowForPath(accumPath));
            accumRect = accumRect.intersection(new Rectangle(rowRect.x, rowRect.y, Integer.MAX_VALUE, Integer.MAX_VALUE));

            //unite all expanded children node rectangles since they can stretch out of parent's
            node = (DefaultMutableTreeNode)accumPath.getLastPathComponent();
            accumRect = accumRect.union(getExpandedNodesRect(this, node, accumPath));

            g.setColor(new Color(230, 230, 230));
            g.fillRoundRect(accumRect.x, accumRect.y, accumRect.width, accumRect.height, 10, 10);
            g.setColor(Color.lightGray);
            g.drawRoundRect(accumRect.x, accumRect.y, accumRect.width, accumRect.height, 10, 10);
          }
        }
        g.setColor(old);
        super.paintComponent(g);
      }
    };
    tree.setOpaque(false);

    tree.setToggleClickCount(-1);
    SliceUsageCellRenderer renderer = new SliceUsageCellRenderer();
    renderer.setOpaque(false);
    tree.setCellRenderer(renderer);
    UIUtil.setLineStyleAngled(tree);
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setSelectionPath(new TreePath(root.getPath()));
    //ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_METHOD_HIERARCHY_POPUP);
    //PopupHandler.installPopupHandler(tree, group, ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    EditSourceOnDoubleClickHandler.install(tree);

    new TreeSpeedSearch(tree);
    TreeUtil.installActions(tree);
    TreeToolTipHandler.install(tree);
    ToolTipManager.sharedInstance().registerComponent(tree);

    myAutoScrollToSourceHandler.install(tree);

    tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        treeSelectionChanged();
      }
    });
    return tree;
  }

  private void treeSelectionChanged() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (isDisposed) return;
        List<UsageInfo> infos = getSelectedUsageInfos();
        if (infos != null && myUsagePreviewPanel != null) {
          myUsagePreviewPanel.updateLayout(infos);
        }
      }
    });
  }

  private static Rectangle getExpandedNodesRect(Tree tree, DefaultMutableTreeNode node, TreePath path) {
    Rectangle rect = tree.getRowBounds(tree.getRowForPath(path));
    if (tree.isExpanded(path)) {
      Enumeration<DefaultMutableTreeNode> children = node.children();
      while (children.hasMoreElements()) {
      DefaultMutableTreeNode child = children.nextElement();
      TreePath childPath = path.pathByAddingChild(child);
        rect = rect.union(getExpandedNodesRect(tree, child, childPath));
      }
    }
    return rect;
  }

  private List<UsageInfo> getSelectedUsageInfos() {
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) return null;
    final ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        Object userObject = node.getUserObject();
        if (userObject instanceof SliceNode) {
          result.add(((SliceNode)userObject).getValue().getUsageInfo());
        }
      }
    }
    if (result.isEmpty()) return null;
    return result;
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == PlatformDataKeys.NAVIGATABLE_ARRAY) {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return;
      final ArrayList<Navigatable> navigatables = new ArrayList<Navigatable>();
      for (TreePath path : paths) {
        Object lastPathComponent = path.getLastPathComponent();
        if (lastPathComponent instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
          Object userObject = node.getUserObject();
          if (userObject instanceof Navigatable) {
            navigatables.add((Navigatable)userObject);
          }
          else if (node instanceof Navigatable) {
            navigatables.add((Navigatable)node);
          }
        }
      }
      if (!navigatables.isEmpty()) {
        sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, navigatables.toArray(new Navigatable[navigatables.size()]));
      }
    }
  }

  public void sliceFinished() {
    TreeUtil.expand(myTree, 1);
  }

  private ActionToolbar createToolbar() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new RefreshAction(myTree));
    actionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    actionGroup.add(new CloseAction());
    actionGroup.add(new ToggleAction(UsageViewBundle.message("preview.usages.action.text"), "preview", IconLoader.getIcon("/actions/preview.png")) {
      public boolean isSelected(AnActionEvent e) {
        return isPreview();
      }

      public void setSelected(AnActionEvent e, boolean state) {
        setPreview(state);
        layoutPanel();
      }
    });

    //actionGroup.add(new ContextHelpAction(HELP_ID));

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.TYPE_HIERARCHY_VIEW_TOOLBAR, actionGroup, false);
  }

  public abstract boolean isAutoScroll();

  public abstract void setAutoScroll(boolean autoScroll);

  public abstract boolean isPreview();

  public abstract void setPreview(boolean preview);

  private class CloseAction extends CloseTabToolbarAction {
    public final void actionPerformed(final AnActionEvent e) {
      dispose();
    }
  }

  private final class RefreshAction extends com.intellij.ide.actions.RefreshAction {
    public RefreshAction(JComponent tree) {
      super(IdeBundle.message("action.refresh"), IdeBundle.message("action.refresh"), IconLoader.getIcon("/actions/sync.png"));
      registerShortcutOn(tree);
    }

    public final void actionPerformed(final AnActionEvent e) {
      myBuilder.addSubtreeToUpdate((DefaultMutableTreeNode)myTree.getModel().getRoot());
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(true);
    }
  }
}
