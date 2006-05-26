package com.intellij.ide.commander;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public abstract class AbstractListBuilder {
  protected final Project myProject;
  protected final JList myList;
  private final DefaultListModel myModel;
  protected final AbstractTreeStructure myTreeStructure;
  private final Comparator myComparator;

  protected JLabel myParentTitle = null;
  private boolean myIsDisposed;
  private AbstractTreeNode myCurrentParent = null;
  private final AbstractTreeNode myShownRoot;

  AbstractListBuilder(
    final Project project,
    final JList list,
    final DefaultListModel model,
    final AbstractTreeStructure treeStructure,
    final Comparator comparator,
    final boolean showRoot
    ) {
    myProject = project;
    myList = list;
    myModel = model;
    myTreeStructure = treeStructure;
    myComparator = comparator;

    final Object rootElement = myTreeStructure.getRootElement();
    final Object[] rootChildren = myTreeStructure.getChildElements(rootElement);

    if (!showRoot && rootChildren.length == 1) {
      myShownRoot = (AbstractTreeNode)rootChildren[0];
    }
    else {
      myShownRoot = (AbstractTreeNode)rootElement;
    }
  }

  public final void setParentTitle(final JLabel parentTitle) {
    myParentTitle = parentTitle;
  }

  final void drillDown() {
    final Object value = myList.getSelectedValue();
    if (value instanceof AbstractTreeNode) {
      try {
        final AbstractTreeNode node = (AbstractTreeNode)value;
        buildList(node);
        ListScrollingUtil.ensureSelectionExists(myList);
      }
      finally {
        updateParentTitle();
      }
    }
    else { // an element that denotes parent
      goUp();
    }
  }

  final void goUp() {
    if (myCurrentParent == myShownRoot.getParent()) {
      return;
    }
    final AbstractTreeNode element = myCurrentParent.getParent();
    if (element == null) {
      return;
    }

    try {
      AbstractTreeNode oldParent = myCurrentParent;

      buildList(element);

      for (int i = 0; i < myModel.size(); i++) {
        if (myModel.getElementAt(i) instanceof NodeDescriptor) {
          final NodeDescriptor desc = (NodeDescriptor)myModel.getElementAt(i);
          final Object elem = desc.getElement();
          if (oldParent.equals(elem)) {
            ListScrollingUtil.selectItem(myList, i);
          break;
          }
        }
      }
    }
    finally {
      updateParentTitle();
    }
  }

  public final void selectElement(final Object element, VirtualFile virtualFile) {
    if (element == null) {
      return;
    }

    try {
      AbstractTreeNode node = goDownToElement(element, virtualFile);
      if (node == null) return;
      AbstractTreeNode parentElement = node.getParent();
      if (parentElement == null) return;

      buildList(parentElement);

      for (int i = 0; i < myModel.size(); i++) {
        if (myModel.getElementAt(i) instanceof AbstractTreeNode) {
          final AbstractTreeNode desc = (AbstractTreeNode)myModel.getElementAt(i);
          if (desc.getValue() instanceof StructureViewTreeElement) {
            StructureViewTreeElement treeelement = (StructureViewTreeElement)desc.getValue();
            if (element.equals(treeelement.getValue())) {
              ListScrollingUtil.selectItem(myList, i);
            break;
            }
          }
          else {
            if (element.equals(desc.getValue())) {
              ListScrollingUtil.selectItem(myList, i);
            break;
            }
          }
        }
      }
    }
    finally {
      updateParentTitle();
    }
  }

  public final void enterElement(final PsiElement element, VirtualFile file) {
    try {
      AbstractTreeNode lastPathNode = null;
      lastPathNode = goDownToElement(element, file);
      if (lastPathNode == null) return;
      buildList(lastPathNode);
      ListScrollingUtil.ensureSelectionExists(myList);
    }
    finally {
      updateParentTitle();
    }
  }

  private AbstractTreeNode goDownToElement(final Object element, VirtualFile file) {
    return goDownToNode((AbstractTreeNode)myTreeStructure.getRootElement(), element, file);
  }

  public final void enterElement(final AbstractTreeNode element) {
    try {
      buildList(element);
      ListScrollingUtil.ensureSelectionExists(myList);
    }
    finally {
      updateParentTitle();
    }
  }

  private AbstractTreeNode goDownToNode(AbstractTreeNode lastPathNode, final Object lastPathElement, VirtualFile file) {
    if (file == null) return lastPathNode;
    AbstractTreeNode found = lastPathNode;
    while (found != null) {
      if (nodeIsAcceptableForElement(lastPathNode, lastPathElement)) {
        break;
      }
      else {
        found = findInChildren(lastPathNode, file, lastPathElement);
        if (found != null) {
          lastPathNode = found;
        }
      }
    }
    return lastPathNode;
  }

  private AbstractTreeNode findInChildren(AbstractTreeNode rootElement, VirtualFile file, Object element) {
    Object[] childElements = getChildren(rootElement);
    List<AbstractTreeNode> nodes = getAllAcceptableNodes(childElements, file);
    if (nodes.size() == 1) return nodes.get(0);
    if (nodes.size() == 0) return null;
    if (!file.isDirectory()) {
      return performDeepSearch(nodes.toArray(), element);
    }
    else {
      return nodes.get(0);
    }
  }

  private AbstractTreeNode performDeepSearch(Object[] nodes, Object element) {
    for (int i = 0; i < nodes.length; i++) {
      AbstractTreeNode node = (AbstractTreeNode)nodes[i];
      if (nodeIsAcceptableForElement(node, element)) return node;
      AbstractTreeNode nodeResult = performDeepSearch(getChildren(node), element);
      if (nodeResult != null) {
        return nodeResult;
      }
    }
    return null;
  }

  protected abstract boolean nodeIsAcceptableForElement(AbstractTreeNode node, Object element);

  protected abstract List<AbstractTreeNode> getAllAcceptableNodes(Object[] childElements, VirtualFile file);

  private NodeDescriptor createDescriptor(final Object element) {
    final Object parent = myTreeStructure.getParentElement(element);
    NodeDescriptor parentDescriptor = null;
    if (parent != null) {
      parentDescriptor = createDescriptor(parent);
    }
    return myTreeStructure.createDescriptor(element, parentDescriptor);
  }

  public void dispose() {
    myIsDisposed = true;
  }

  private void buildList(final AbstractTreeNode parentElement) {
    myCurrentParent = parentElement;
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    alarm.addRequest(
        new Runnable() {
        public void run() {
          myList.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
      },
      200
    );

    final Object[] children = getChildren(parentElement);
    myModel.removeAllElements();
    if (shouldAddTopElement()) {
      myModel.addElement(new TopLevelNode(myProject, parentElement.getValue()));
    }

    for (int i = 0; i < children.length; i++) {
      AbstractTreeNode child = (AbstractTreeNode)children[i];
      child.update();
    }
    if (myComparator != null) {
      Arrays.sort(children, myComparator);
    }
    for (int i = 0; i < children.length; i++) {
      myModel.addElement(children[i]);
    }

    final int n = alarm.cancelAllRequests();
    if (n == 0) {
      alarm.addRequest(
          new Runnable() {
          public void run() {
            myList.setCursor(Cursor.getDefaultCursor());
          }
        },
        0
      );
    }
  }

  private boolean shouldAddTopElement() {
    return !myShownRoot.equals(myCurrentParent);
  }

  private Object[] getChildren(final AbstractTreeNode parentElement) {
     if (parentElement == null) {
       return new Object[]{myTreeStructure.getRootElement()};
     }
     else {
       return myTreeStructure.getChildElements(parentElement);
     }
  }

  protected final void updateList() {
    if (myIsDisposed || myCurrentParent == null) {
      return;
    }
    if (myTreeStructure.hasSomethingToCommit()) {
      myTreeStructure.commit();
    }

    final AbstractTreeNode initialParentDescriptor = myCurrentParent;
    AbstractTreeNode parentDescriptor = initialParentDescriptor;

    while (true) {
      parentDescriptor.update();
      if (parentDescriptor.getValue() != null) break;
      parentDescriptor = parentDescriptor.getParent();
    }

    final Object[] children = getChildren(parentDescriptor);
    final com.intellij.util.containers.HashMap<Object,Integer> elementToIndexMap = new com.intellij.util.containers.HashMap<Object, Integer>();
    for (int i = 0; i < children.length; i++) {
      elementToIndexMap.put(children[i], new Integer(i));
    }

    final List resultDescriptors = new ArrayList();
    final Object[] listChildren = myModel.toArray();
    for (int i = 0; i < listChildren.length; i++) {
      final Object child = listChildren[i];
      if (!(child instanceof NodeDescriptor)) {
      continue;
      }
      final NodeDescriptor descriptor = (NodeDescriptor)child;
      descriptor.update();
      final Object newElement = descriptor.getElement();
      final Integer index = (newElement != null) ? elementToIndexMap.get(newElement) : null;
      if (index != null) {
        resultDescriptors.add(descriptor);
        descriptor.setIndex(index.intValue());
        elementToIndexMap.remove(newElement);
      }
    }

    for (Iterator iterator = elementToIndexMap.keySet().iterator(); iterator.hasNext();) {
      final Object child = iterator.next();
      final Integer index = elementToIndexMap.get(child);
      if (index != null) {
        final NodeDescriptor childDescr = myTreeStructure.createDescriptor(child, parentDescriptor);
        childDescr.setIndex(index.intValue());
        childDescr.update();
        resultDescriptors.add(childDescr);
      }
    }

    final SelectionInfo selection = storeSelection();
    if (myComparator != null) {
      Collections.sort(resultDescriptors, myComparator);
    }
    else {
      Collections.sort(resultDescriptors, IndexComparator.INSTANCE);
    }

    myModel.removeAllElements();
    if (shouldAddTopElement()) {
      myModel.addElement(new TopLevelNode(myProject, parentDescriptor.getValue()));
    }
    for (int i = 0; i < resultDescriptors.size(); i++) {
      final NodeDescriptor descriptor = (NodeDescriptor)resultDescriptors.get(i);
      myModel.addElement(descriptor);
    }
    restoreSelection(selection);
    updateParentTitle();
  }

  private static final class SelectionInfo {
    public final ArrayList mySelectedObjects;
    public final Object myLeadSelection;
    public final int myLeadSelectionIndex;

    public SelectionInfo(final ArrayList selectedObjects, final int leadSelectionIndex, final Object leadSelection) {
      myLeadSelection = leadSelection;
      myLeadSelectionIndex = leadSelectionIndex;
      mySelectedObjects = selectedObjects;
    }
  }

  private SelectionInfo storeSelection() {
    final ListSelectionModel selectionModel = myList.getSelectionModel();
    final ArrayList selectedObjects = new ArrayList();
    final int[] selectedIndices = myList.getSelectedIndices();
    final int leadSelectionIndex = selectionModel.getLeadSelectionIndex();
    Object leadSelection = null;
    for (int i = 0; i < selectedIndices.length; i++) {
      final int index = selectedIndices[i];
      if (index < myList.getModel().getSize()) {
        final Object o = myModel.get(index);
        selectedObjects.add(o);
        if (index == leadSelectionIndex) {
          leadSelection = o;
        }
      }
    }
    return new SelectionInfo(selectedObjects, leadSelectionIndex, leadSelection);
  }

  private void restoreSelection(final SelectionInfo selection) {
    final ArrayList selectedObjects = selection.mySelectedObjects;
    int leadIndex = -1;

    final ListSelectionModel selectionModel = myList.getSelectionModel();

    selectionModel.clearSelection();
    if (selectedObjects.size() > 0) {
      for (int i = 0; i < selectedObjects.size(); i++) {
        final Object o = selectedObjects.get(i);
        final int index = myModel.indexOf(o);
        if (index > -1) {
          selectionModel.addSelectionInterval(index, index);
          if (o == selection.myLeadSelection) {
            leadIndex = index;
          }
        }
      }

      if (selectionModel.getMinSelectionIndex() == -1) {
        final int toSelect = Math.min(selection.myLeadSelectionIndex, myModel.size() - 1);
        if (toSelect >= 0) {
          myList.setSelectedIndex(toSelect);
        }
      }
      else if (leadIndex != -1) {
        selectionModel.setLeadSelectionIndex(leadIndex);
      }
    }
  }

  public final AbstractTreeNode getParentNode() {
    return myCurrentParent;
  }

  protected abstract void updateParentTitle();

  public final void buildRoot() {
    buildList(myShownRoot);
  }
}