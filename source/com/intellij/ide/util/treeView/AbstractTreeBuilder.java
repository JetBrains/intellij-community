package com.intellij.ide.util.treeView;

import com.intellij.ide.LoadingNode;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.WorkerThread;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.enumeration.EnumerationCopy;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class AbstractTreeBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeBuilder");

  protected final JTree myTree;
  protected final DefaultTreeModel myTreeModel;
  protected AbstractTreeStructure myTreeStructure;

  protected AbstractTreeUpdater myUpdater;

  private Comparator<NodeDescriptor> myNodeDescriptorComparator;
  private final Comparator<TreeNode> myNodeComparator = new Comparator<TreeNode>() {
    public int compare(TreeNode n1, TreeNode n2) {
      if (n1 instanceof LoadingNode || n2 instanceof LoadingNode) return 0;
      NodeDescriptor nodeDescriptor1 = (NodeDescriptor)((DefaultMutableTreeNode)n1).getUserObject();
      NodeDescriptor nodeDescriptor2 = (NodeDescriptor)((DefaultMutableTreeNode)n2).getUserObject();
      return (myNodeDescriptorComparator !=  null)? myNodeDescriptorComparator.compare(nodeDescriptor1, nodeDescriptor2) : nodeDescriptor1.getIndex() - nodeDescriptor2.getIndex();
    }
  };

  protected final DefaultMutableTreeNode myRootNode;

  private final HashMap<Object,Object> myElementToNodeMap = new HashMap<Object, Object>();
  protected final HashSet<DefaultMutableTreeNode> myUnbuiltNodes = new HashSet<DefaultMutableTreeNode>();
  private final TreeExpansionListener myExpansionListener;

  private WorkerThread myWorker = null;
  private final ProgressIndicator myProgress = new StatusBarProgress();

  private static final int WAIT_CURSOR_DELAY = 100;

  private boolean myDisposed = false;
  private static final AbstractTreeNodeWrapper TREE_NODE_WRAPPER = new AbstractTreeNodeWrapper(null);

  public AbstractTreeBuilder(JTree tree, DefaultTreeModel treeModel,
                             AbstractTreeStructure treeStructure, Comparator<NodeDescriptor> comparator) {
    myTree = tree;
    myTreeModel = treeModel;
    myRootNode = (DefaultMutableTreeNode)treeModel.getRoot();
    myTreeStructure = treeStructure;
    myNodeDescriptorComparator = comparator;

    myExpansionListener = new MyExpansionListener();
    myTree.addTreeExpansionListener(myExpansionListener);

    myUpdater = createUpdater();
  }

  protected AbstractTreeUpdater createUpdater() {
    return new AbstractTreeUpdater(this);
  }

  public void dispose() {
    LOG.assertTrue(!myDisposed);
    myDisposed = true;
    myTree.removeTreeExpansionListener(myExpansionListener);
    disposeNode(myRootNode);
    myElementToNodeMap.clear();
    myUpdater.cancelAllRequests();
    if (myWorker != null) {
      myWorker.dispose(true);
    }
    myProgress.cancel();
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  protected abstract boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor);

  protected abstract boolean isAutoExpandNode(NodeDescriptor nodeDescriptor);

  protected boolean isDisposeOnCollapsing(NodeDescriptor nodeDescriptor) {
    return true;
  }

  protected boolean isSmartExpand() {
    return true;
  }

  protected void expandNodeChildren(final DefaultMutableTreeNode node) {
    myTreeStructure.commit();
    myUpdater.addSubtreeToUpdate(node);
    myUpdater.performUpdate();
  }

  public final AbstractTreeStructure getTreeStructure() {
    return myTreeStructure;
  }

  public final JTree getTree() {
    return myTree;
  }

  public final DefaultMutableTreeNode getNodeForElement(Object element) {
    //DefaultMutableTreeNode node = (DefaultMutableTreeNode)myElementToNodeMap.get(element);
    DefaultMutableTreeNode node = getFirstNode(element);
    if (node != null) {
      LOG.assertTrue(TreeUtil.isAncestor(myRootNode, node));
      LOG.assertTrue(myRootNode == myTreeModel.getRoot());
    }
    return node;
  }

  public final DefaultMutableTreeNode getNodeForPath(Object[] path) {
    DefaultMutableTreeNode node = null;
    for (int idx = 0; idx < path.length; idx++) {
      final Object pathElement = path[idx];
      node = (node == null) ? getFirstNode(pathElement) : findNodeForChildElement(node, pathElement);
      if (node == null) {
        break;
      }
    }
    return node;
  }

  public final void buildNodeForElement(Object element) {
    myUpdater.performUpdate();
    DefaultMutableTreeNode node = getNodeForElement(element);
    if (node == null) {
      final List<Object> elements = new ArrayList<Object>();
      while (true) {
        element = myTreeStructure.getParentElement(element);
        if (element == null) {
          break;
        }
        elements.add(0, element);
      }

      for (int i = 0; i < elements.size(); i++) {
        final Object element1 = elements.get(i);
        node = getNodeForElement(element1);
        if (node != null) {
          myTree.expandPath(new TreePath(node.getPath()));
        }
      }
    }
  }

  public final void buildNodeForPath(Object[] path) {
    myUpdater.performUpdate();
    DefaultMutableTreeNode node = null;
    for (int idx = 0; idx < path.length; idx++) {
      final Object pathElement = path[idx];
      node = node == null ? getFirstNode(pathElement) : findNodeForChildElement(node, pathElement);
      if (node != null) {
        myTree.expandPath(new TreePath(node.getPath()));
      }
    }
  }

  public final void setNodeDescriptorComparator(Comparator<NodeDescriptor> nodeDescriptorComparator) {
    myNodeDescriptorComparator = nodeDescriptorComparator;
    ArrayList pathsToExpand = new ArrayList();
    ArrayList selectionPaths = new ArrayList();
    TreeBuilderUtil.storePaths(this, myRootNode, pathsToExpand, selectionPaths, false);
    resortChildren(myRootNode);
    myTreeModel.nodeStructureChanged(myRootNode);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, false);
  }

  private void resortChildren(DefaultMutableTreeNode node) {
    ArrayList<TreeNode> childNodes = TreeUtil.childrenToArray(node);
    node.removeAllChildren();
    Collections.sort(childNodes, myNodeComparator);
    for (int i = 0; i < childNodes.size(); i++) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)childNodes.get(i);
      node.add(childNode);
      resortChildren(childNode);
    }
  }

  protected final void initRootNode() {
    Object rootElement = myTreeStructure.getRootElement();
    NodeDescriptor nodeDescriptor = myTreeStructure.createDescriptor(rootElement, null);
    myRootNode.setUserObject(nodeDescriptor);
    nodeDescriptor.update();
    if (rootElement != null) {
      //myElementToNodeMap.put(rootElement, myRootNode);
      createMapping(rootElement, myRootNode);
    }
    addLoadingNode(myRootNode);
    boolean willUpdate = false;
    if (isAutoExpandNode(nodeDescriptor)) {
      willUpdate = myUnbuiltNodes.contains(myRootNode);
      myTree.expandPath(new TreePath(myRootNode.getPath()));
    }
    if (!willUpdate) {
      updateNodeChildren(myRootNode);
    }
    if (myRootNode.getChildCount() == 0) {
      myTreeModel.nodeChanged(myRootNode);
    }
  }

// Made non-final for Fabrique
  public void updateFromRoot() {
    updateSubtree(myRootNode);
  }

// Made non-final for Fabrique
  protected void updateSubtree(DefaultMutableTreeNode node) {
    if (!(node.getUserObject() instanceof NodeDescriptor)) return;
    updateNode(node);
    updateNodeChildren(node);
  }

// Made non-final for Fabrique
  protected void updateNode(DefaultMutableTreeNode node) {
    if (!(node.getUserObject() instanceof NodeDescriptor)) return;
    NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    Object prevElement = descriptor.getElement();
    if (prevElement == null) return;
    boolean changes = descriptor.update();
    if (descriptor.getElement() == null) {
      LOG.assertTrue(false,
                     "element == null, updateSubtree should be invoked for parent! builder=" + this +
                     ", prevElement = " + prevElement +
                     ", node = " + node);
    }
    if (changes) {
      updateNodeImageAndPosition(node);
    }
  }

  private void updateNodeChildren(final DefaultMutableTreeNode node) {
    myTreeStructure.commit();
    boolean wasExpanded = myTree.isExpanded(new TreePath(node.getPath()));
    final boolean wasLeaf = node.getChildCount() == 0;

    final NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    if (descriptor == null) return;

    if (myUnbuiltNodes.contains(node)) {
      if (isAlwaysShowPlus(descriptor)) return; // check for isAlwaysShowPlus is important for e.g. changing Show Members state!

      Object element = descriptor.getElement();
      if (myTreeStructure.isToBuildChildrenInBackground(element)) return; //?

      Object[] children = myTreeStructure.getChildElements(descriptor.getElement());
      if (children.length == 0) {
        for (int i = 0; i < node.getChildCount(); i++) {
          if (node.getChildAt(i) instanceof LoadingNode) {
            myTreeModel.removeNodeFromParent((MutableTreeNode)node.getChildAt(i));
            break;
          }
        }
        myUnbuiltNodes.remove(node);
      }
      return;
    }

    Object element = descriptor.getElement();
    if (myTreeStructure.isToBuildChildrenInBackground(element)) {
      Runnable updateRunnable = new Runnable() {
        public void run() {
          descriptor.update();
          Object element = descriptor.getElement();
          if (element == null) return;

          myTreeStructure.getChildElements(element); // load children
        }
      };

      Runnable postRunnable = new Runnable() {
        public void run() {
          descriptor.update();
          Object element = descriptor.getElement();
          if (element != null) {
            myUnbuiltNodes.remove(node);
            myUpdater.addSubtreeToUpdateByElement(element);
            myUpdater.performUpdate();

            for (int i = 0; i < node.getChildCount(); i++) {
              TreeNode child = node.getChildAt(i);
              if (child instanceof LoadingNode) {
                if (TreeBuilderUtil.isNodeOrChildSelected(myTree, node)) {
                  myTree.addSelectionPath(new TreePath(myTreeModel.getPathToRoot(node)));
                }
                myTreeModel.removeNodeFromParent((MutableTreeNode)child);
                break;
              }
            }
          }
        }
      };

      String text = " searching...";
      for (int i = 0; i < node.getChildCount(); i++) {
        TreeNode child = node.getChildAt(i);
        if (child instanceof LoadingNode && text.equals(((LoadingNode)child).getUserObject())) {
          return;
        }
      }
      LoadingNode loadingNode = new LoadingNode(text);
      myTreeModel.insertNodeInto(loadingNode, node, node.getChildCount()); // 2 loading nodes - only one will be removed

      addTaskToWorker(updateRunnable, true, postRunnable);

      return;
    }

    Object[] children = myTreeStructure.getChildElements(descriptor.getElement());
    Map<Object,Integer> elementToIndexMap = new LinkedHashMap<Object, Integer>();
    for (int i = 0; i < children.length; i++) {
      Object child = children[i];
      elementToIndexMap.put(child, new Integer(i));
    }

    ArrayList<TreeNode> childNodes = TreeUtil.childrenToArray(node);
    for (int i = 0; i < childNodes.size(); i++) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)childNodes.get(i);
      if (childNode instanceof LoadingNode) continue;
      NodeDescriptor childDescr = (NodeDescriptor)childNode.getUserObject();
      if (childDescr == null) {
        boolean isInMap = myElementToNodeMap.containsValue(childNode);
        LOG.error("childDescr == null, builder=" + this + ", childNode=" + childNode.getClass() + ", isInMap = " + isInMap + ", node = " + node);
        continue;
      }
      Object oldElement = childDescr.getElement();
      if (oldElement == null){
        LOG.error("oldElement == null, builder=" + this + ", childDescr=" + childDescr);
        continue;
      }
      boolean changes = childDescr.update();
      Object newElement = childDescr.getElement();
      Integer index = newElement != null ? elementToIndexMap.get(newElement) : null;
      if (index != null) {
        if (childDescr.getIndex() != index.intValue()) {
          changes = true;
        }
        childDescr.setIndex(index.intValue());
      }
      if (newElement != null && changes) {
        updateNodeImageAndPosition(childNode);
      }
      if (!oldElement.equals(newElement)) {
        removeMapping(oldElement, childNode);
        if (newElement != null) {
          createMapping(newElement, childNode);
        }
      }

      if (index == null) {
        int selectedIndex = -1;
        if (TreeBuilderUtil.isNodeOrChildSelected(myTree, childNode)) {
          selectedIndex = node.getIndex(childNode);
        }

        myTreeModel.removeNodeFromParent(childNode);
        disposeNode(childNode);

        if (selectedIndex >= 0) {
          if (node.getChildCount() > 0) {
            if (node.getChildCount() > selectedIndex) {
              TreeNode newChildNode = node.getChildAt(selectedIndex);
              myTree.addSelectionPath(new TreePath(myTreeModel.getPathToRoot(newChildNode)));
            }
            else {
              TreeNode newChild = node.getChildAt(node.getChildCount() - 1);
              myTree.addSelectionPath(new TreePath(myTreeModel.getPathToRoot(newChild)));
            }
          }
          else {
            myTree.addSelectionPath(new TreePath(myTreeModel.getPathToRoot(node)));
          }
        }
      }
      else {
        elementToIndexMap.remove(newElement);
        updateNodeChildren(childNode);
      }

      if (node.equals(myRootNode)) {
        myTreeModel.nodeChanged(myRootNode);
      }
    }

    ArrayList<TreeNode> nodesToInsert = new ArrayList<TreeNode>();
    for (Iterator<Object> iterator = elementToIndexMap.keySet().iterator(); iterator.hasNext();) {
      Object child = iterator.next();
      Integer index = elementToIndexMap.get(child);
      final NodeDescriptor childDescr = myTreeStructure.createDescriptor(child, descriptor);
      if (childDescr == null) {
        LOG.error("childDescr == null, treeStructure = " + myTreeStructure + ", child = " + child);
        continue;
      }
      childDescr.setIndex(index.intValue());
      childDescr.update();
      if (childDescr.getElement() == null) {
        LOG.error("childDescr.getElement() == null, child = " + child + ", builder = " + this);
        continue;
      }
      final DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(childDescr);
      nodesToInsert.add(childNode);
      createMapping(child, childNode);
    }

    insertNodesInto(nodesToInsert, node);

    for (int i = 0; i < nodesToInsert.size(); i++) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)nodesToInsert.get(i);
      addLoadingNode(childNode);
      updateNodeChildren(childNode);
    }

    if (wasExpanded) {
      myTree.expandPath(new TreePath(node.getPath()));
    }

    if (wasExpanded || wasLeaf) {
      final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
      alarm.addRequest(
        new Runnable() {
          public void run() {
            myTree.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
          }
        },
        WAIT_CURSOR_DELAY
      );

      if (wasLeaf && isAutoExpandNode(descriptor)) {
        myTree.expandPath(new TreePath(node.getPath()));
      }

      ArrayList<TreeNode> nodes = TreeUtil.childrenToArray(node);
      for (int i = 0; i < nodes.size(); i++) {
        DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)nodes.get(i);
        if (childNode instanceof LoadingNode) continue;
        NodeDescriptor childDescr = (NodeDescriptor)childNode.getUserObject();
        if (isAutoExpandNode(childDescr)) {
          myTree.expandPath(new TreePath(childNode.getPath()));
        }
      }

      int n = alarm.cancelAllRequests();
      if (n == 0) {
        myTree.setCursor(Cursor.getDefaultCursor());
      }
    }

  }

  private void addLoadingNode(DefaultMutableTreeNode node) {
    final NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    if (!isAlwaysShowPlus(descriptor)) {
      Object element = descriptor.getElement();
      if (myTreeStructure.isToBuildChildrenInBackground(element)) {
        final boolean[] hasNoChildren = new boolean[1];
        Runnable runnable = new Runnable() {
          public void run() {
            descriptor.update();
            Object element = descriptor.getElement();
            if (element == null) return;

            Object[] children = myTreeStructure.getChildElements(element);
            hasNoChildren[0] = children.length == 0;
          }
        };

        Runnable postRunnable = new Runnable() {
          public void run() {
            if (hasNoChildren[0]) {
              descriptor.update();
              Object element = descriptor.getElement();
              if (element != null) {
                DefaultMutableTreeNode node = getNodeForElement(element);
                if (node != null) {
                  myTree.expandPath(new TreePath(node.getPath()));
                }
              }
            }
          }
        };

        addTaskToWorker(runnable, false, postRunnable);
      }
      else {
        Object[] children = myTreeStructure.getChildElements(descriptor.getElement());
        if (children.length == 0) return;
      }
    }

    myTreeModel.insertNodeInto(new LoadingNode(), node, 0);
    myUnbuiltNodes.add(node);
  }

  protected void addTaskToWorker(final Runnable runnable, boolean first, final Runnable postRunnable) {
    Runnable runnable1 = new Runnable() {
      public void run() {
        try {
          ProgressManager.getInstance().runProcess(
            new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runReadAction(runnable);
                if (postRunnable != null) {
                  ApplicationManager.getApplication().invokeLater(postRunnable);
                }
              }
            },
            myProgress
          );
        }
        catch (ProcessCanceledException e) {
        }
      }
    };

    if (myWorker == null || myWorker.isDisposed()) {
      myWorker = new WorkerThread("AbstractTreeBuilder.Worker");
      myWorker.start();
      if (first) {
        myWorker.addTaskFirst(runnable1);
      }
      else {
        myWorker.addTask(runnable1);
      }
      myWorker.dispose(false);
    }
    else {
      if (first) {
        myWorker.addTaskFirst(runnable1);
      }
      else {
        myWorker.addTask(runnable1);
      }
    }
  }

  private void updateNodeImageAndPosition(final DefaultMutableTreeNode node) {
    if (!(node.getUserObject() instanceof NodeDescriptor)) return;
    NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    if (descriptor.getElement() == null) return;
    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)node.getParent();
    if (parentNode != null) {
      int oldIndex = parentNode.getIndex(node);

      int newIndex = 0;
      for (int i = 0; i < parentNode.getChildCount(); i++) {
        TreeNode node1 = parentNode.getChildAt(i);
        if (node == node1) continue;
        if (myNodeComparator.compare(node, node1) > 0) newIndex++;
      }

      if (oldIndex != newIndex) {
        ArrayList pathsToExpand = new ArrayList();
        ArrayList selectionPaths = new ArrayList();
        TreeBuilderUtil.storePaths(this, node, pathsToExpand, selectionPaths, false);
        myTreeModel.removeNodeFromParent(node);
        myTreeModel.insertNodeInto(node, parentNode, newIndex);
        TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, false);
      }
      else {
        myTreeModel.nodeChanged(node);
      }
    }
    else {
      myTreeModel.nodeChanged(node);
    }
  }

  private void insertNodesInto(ArrayList<TreeNode> nodes, DefaultMutableTreeNode parentNode) {
    if (nodes.size() == 0) return;
    nodes = (ArrayList<TreeNode>)nodes.clone();
    Collections.sort(nodes, myNodeComparator);
    int[] indices = new int[nodes.size()];
    for (int i = 0; i < nodes.size(); i++) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)nodes.get(i);
      int index = getIndexToInsert(node, parentNode);
      indices[i] = index;
      parentNode.insert(node, index);
    }
    myTreeModel.nodesWereInserted(parentNode, indices);
  }

  private int getIndexToInsert(DefaultMutableTreeNode node, DefaultMutableTreeNode parentNode) {
    ArrayList<TreeNode> arrayList = TreeUtil.childrenToArray(parentNode);
    arrayList.add(node);
    Collections.sort(arrayList, myNodeComparator);
    return arrayList.indexOf(node);
  }

  private void disposeNode(DefaultMutableTreeNode node) {
    if (node.getChildCount() > 0) {
      for (DefaultMutableTreeNode _node = (DefaultMutableTreeNode)node.getFirstChild();
           _node != null;
           _node = _node.getNextSibling()) {
        disposeNode(_node);
      }
    }
    if (node instanceof LoadingNode) return;
    NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
    final Object element = descriptor.getElement();
    removeMapping(element, node);
    node.setUserObject(null);
    node.removeAllChildren();
  }

  private class MyExpansionListener implements TreeExpansionListener {
    public void treeExpanded(TreeExpansionEvent event) {
      TreePath path = event.getPath();
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (!myUnbuiltNodes.contains(node)) return;
      myUnbuiltNodes.remove(node);
      final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
      alarm.addRequest(
        new Runnable() {
          public void run() {
            myTree.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
          }
        },
        WAIT_CURSOR_DELAY
      );

      expandNodeChildren(node);

      for (int i = 0; i < node.getChildCount(); i++) {
        if (node.getChildAt(i) instanceof LoadingNode) {
          myTreeModel.removeNodeFromParent((MutableTreeNode)node.getChildAt(i));
          break;
        }
      }

      int n = alarm.cancelAllRequests();
      if (n == 0) {
        myTree.setCursor(Cursor.getDefaultCursor());
      }

      if (isSmartExpand() && node.getChildCount() == 1) { // "smart" expand
        TreeNode childNode = node.getChildAt(0);
        final TreePath childPath = path.pathByAddingChild(childNode);
        myTree.expandPath(childPath);
      }
    }

    public void treeCollapsed(TreeExpansionEvent e) {
      TreePath path = e.getPath();
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (isSelectionInside(node)) {
        // when running outside invokeLater, in EJB view just collapsed node get expanded again (bug 4585)
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                myTree.addSelectionPath(new TreePath(myTreeModel.getPathToRoot(node)));
              }
            });
      }
      if (!(node.getUserObject() instanceof NodeDescriptor)) return;
      NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
      if (isDisposeOnCollapsing(descriptor)) {
        removeChildren(node);
        addLoadingNode(node);
        if (node.equals(myRootNode)) {
          myTree.addSelectionPath(new TreePath(myRootNode.getPath()));
        }
        else {
          myTreeModel.reload(node);
        }
      }
    }

    private void removeChildren(DefaultMutableTreeNode node) {
      EnumerationCopy copy = new EnumerationCopy(node.children());
      while (copy.hasMoreElements()) {
        disposeNode((DefaultMutableTreeNode)copy.nextElement());
      }
      node.removeAllChildren();
      myTreeModel.nodeStructureChanged(node);
    }

    private boolean isSelectionInside(DefaultMutableTreeNode parent) {
      TreePath path = new TreePath(myTreeModel.getPathToRoot(parent));
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return false;
      for (int i = 0; i < paths.length; i++) {
        TreePath path1 = paths[i];
        if (path.isDescendant(path1)) return true;
      }
      return false;
    }
  }

  private void createMapping(Object element, DefaultMutableTreeNode node) {
    if (!myElementToNodeMap.containsKey(element)) {
      myElementToNodeMap.put(element, node);
    }
    else {
      final Object value = myElementToNodeMap.get(element);
      final List<DefaultMutableTreeNode> nodes;
      if (value instanceof DefaultMutableTreeNode) {
        nodes = new ArrayList<DefaultMutableTreeNode>();
        nodes.add((DefaultMutableTreeNode)value);
        myElementToNodeMap.put(element, nodes);
      }
      else {
        nodes = (List<DefaultMutableTreeNode>)value;
      }
      nodes.add(node);
    }
  }

  private void removeMapping(Object element, DefaultMutableTreeNode node) {
    final Object value = myElementToNodeMap.get(element);
    if (value == null) {
      return;
    }
    if (value instanceof DefaultMutableTreeNode) {
      if (value.equals(node)) {
        myElementToNodeMap.remove(element);
      }
    }
    else {
      List<DefaultMutableTreeNode> nodes = (List<DefaultMutableTreeNode>)value;
      final boolean reallyRemoved = nodes.remove(node);
      if (reallyRemoved) {
        if (nodes.size() == 0) {
          myElementToNodeMap.remove(element);
        }
      }
    }
  }

  private DefaultMutableTreeNode getFirstNode(Object element) {
    final Object value = findNodeByElement(element);
    if (value == null) {
      return null;
    }
    if (value instanceof DefaultMutableTreeNode) {
      return (DefaultMutableTreeNode)value;
    }
    final List<DefaultMutableTreeNode> nodes = (List<DefaultMutableTreeNode>)value;
    return nodes.size() > 0 ? nodes.get(0) : null;
  }

  private Object findNodeByElement(Object element) {
    if (myElementToNodeMap.containsKey(element)){
      return myElementToNodeMap.get(element);
    }

    TREE_NODE_WRAPPER.setValue(element);
    return myElementToNodeMap.get(TREE_NODE_WRAPPER);
  }

  private DefaultMutableTreeNode findNodeForChildElement(DefaultMutableTreeNode parentNode, Object element) {
    final Object value = myElementToNodeMap.get(element);
    if (value == null) {
      return null;
    }

    if (value instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode elementNode = (DefaultMutableTreeNode)value;
      return parentNode.equals(elementNode.getParent()) ? elementNode : null;
    }

    final List<DefaultMutableTreeNode> allNodesForElement = (List<DefaultMutableTreeNode>)value;
    for (Iterator<DefaultMutableTreeNode> it = allNodesForElement.iterator(); it.hasNext();) {
      final DefaultMutableTreeNode elementNode = it.next();
      if (parentNode.equals(elementNode.getParent())) {
        return elementNode;
      }
    }

    return null;
  }

  private static class AbstractTreeNodeWrapper extends AbstractTreeNode<Object> {
    public AbstractTreeNodeWrapper(Object element) {
      super(null, element);
    }

    public Collection<AbstractTreeNode> getChildren() {
      return null;
    }

    public void update(PresentationData presentation) {
    }
  }
}
