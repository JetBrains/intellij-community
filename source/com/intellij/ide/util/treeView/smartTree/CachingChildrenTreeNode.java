package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.Navigatable;
import gnu.trove.THashMap;

import java.util.*;

public abstract class CachingChildrenTreeNode <Value> extends AbstractTreeNode<Value> {
  protected List<CachingChildrenTreeNode> myChildren;
  protected List<CachingChildrenTreeNode> myOldChildren = null;
  protected final TreeModel myTreeModel;

  public CachingChildrenTreeNode(Project project, Value value, TreeModel treeModel) {
    super(project, value);
    myTreeModel = treeModel;
  }

  public Collection<AbstractTreeNode> getChildren() {
      ensureChildrenAreInitialized();
      return new ArrayList<AbstractTreeNode>(myChildren);
  }

  private void ensureChildrenAreInitialized() {
    if (myChildren == null) {
      myChildren = new ArrayList<CachingChildrenTreeNode>();
      rebuildSubtree();
    }
  }

  public void addSubElement(CachingChildrenTreeNode node) {
    ensureChildrenAreInitialized();
    myChildren.add(node);
    node.setParent(this);
  }

  public void setChildren(Collection<AbstractTreeNode> children) {
    clearChildren();
    for (AbstractTreeNode node : children) {
      myChildren.add((CachingChildrenTreeNode)node);
      node.setParent(this);
    }
  }

  private static class CompositeComparator implements Comparator<CachingChildrenTreeNode> {
    private final Sorter[] mySorters;

    public CompositeComparator(final Sorter[] sorters) {
      mySorters = sorters;
    }

    public int compare(final CachingChildrenTreeNode o1, final CachingChildrenTreeNode o2) {
      final Object value1 = o1.getValue();
      final Object value2 = o2.getValue();
      for (Sorter sorter : mySorters) {
        final int result = sorter.getComparator().compare(value1, value2);
        if (result != 0) return result;
      }
      return 0;
    }
  }

  public void sortChildren(Sorter[] sorters) {
    Collections.sort(myChildren, new CompositeComparator(sorters));

    for (CachingChildrenTreeNode child : myChildren) {
      if (child instanceof GroupWrapper) {
        child.sortChildren(sorters);
      }
    }

  }

  public void filterChildren(Filter[] filters) {
    Collection<AbstractTreeNode> children = getChildren();
    for (Filter filter : filters) {
      for (Iterator<AbstractTreeNode> eachNode = children.iterator(); eachNode.hasNext();) {
        TreeElementWrapper eachChild = (TreeElementWrapper)eachNode.next();
        if (!filter.isVisible(eachChild.getValue())) {
          eachNode.remove();
        }
      }
    }
    setChildren(children);
  }

  public void groupChildren(Grouper[] groupers) {
    for (Grouper grouper : groupers) {
      groupElements(grouper);
    }
  }

  private void groupElements(Grouper grouper) {
    ArrayList<AbstractTreeNode<TreeElement>> ungrouped = new ArrayList<AbstractTreeNode<TreeElement>>();
    Collection<AbstractTreeNode> children = getChildren();
    for (final AbstractTreeNode aChildren : children) {
      CachingChildrenTreeNode<TreeElement> node = (CachingChildrenTreeNode<TreeElement>)aChildren;
      if (node instanceof TreeElementWrapper) {
        ungrouped.add(node);
      }
      else {
        node.groupElements(grouper);
      }
    }

    if (ungrouped.size() != 0) {
      processUngrouped(ungrouped, grouper);
    }

    Collection<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (AbstractTreeNode child : children) {
      AbstractTreeNode parent = child.getParent();
      if (parent != this) {
        if (!result.contains(parent)) result.add(parent);
      }
      else {
        result.add(child);
      }
    }
    setChildren(result);
  }

  private void processUngrouped(List<AbstractTreeNode<TreeElement>> ungrouped, Grouper grouper) {
    Collection<TreeElement> ungroupedObjects = collectValues(ungrouped);
    Collection<Group> groups = grouper.group(ungroupedObjects);

    Map<Group, GroupWrapper> groupNodes = createGroupNodes(groups);

    for (Group group : groups) {
      for (Iterator<AbstractTreeNode<TreeElement>> eachUngrNode = ungrouped.iterator(); eachUngrNode.hasNext();) {
        AbstractTreeNode<TreeElement> node = eachUngrNode.next();
        if (group.contains(node.getValue())) {
          GroupWrapper groupWrapper = groupNodes.get(group);
          groupWrapper.addSubElement((CachingChildrenTreeNode)node);
          node.setParent(groupWrapper);
          eachUngrNode.remove();
        }
      }
    }
  }

  private Collection<TreeElement> collectValues(List<AbstractTreeNode<TreeElement>> ungrouped) {
    ArrayList<TreeElement> objects = new ArrayList<TreeElement>();
    for (final AbstractTreeNode<TreeElement> aUngrouped : ungrouped) {
      objects.add(aUngrouped.getValue());
    }
    return objects;
  }

  private Map<Group, GroupWrapper> createGroupNodes(Collection<Group> groups) {
    Map<Group, GroupWrapper> result = new THashMap<Group, GroupWrapper>();
    for (Group group : groups) {
      result.put(group, new GroupWrapper(getProject(), group, myTreeModel));
    }
    return result;
  }


  private void rebuildSubtree() {
    initChildren();
    performTreeActions();

    synchronizeChildren();

  }

  protected void synchronizeChildren() {
    if (myOldChildren != null && myChildren != null) {
      for (CachingChildrenTreeNode oldInstance : myOldChildren) {
        final int newIndex = getIndexOfPointerToTheSameValue(oldInstance);
        if (newIndex >= 0) {
          final CachingChildrenTreeNode newInstance = myChildren.get(newIndex);
          oldInstance.copyFromNewInstance(newInstance);
          oldInstance.setValue(newInstance.getValue());
          myChildren.set(newIndex, oldInstance);
        }
      }
    }
  }

  private int getIndexOfPointerToTheSameValue(final CachingChildrenTreeNode oldInstance) {
    for (int i = 0; i < myChildren.size(); i++) {
      CachingChildrenTreeNode newInstance = myChildren.get(i);

      if (newInstance instanceof TreeElementWrapper) {
        final StructureViewTreeElement newElement = (StructureViewTreeElement)newInstance.getValue();
        if (oldInstance instanceof TreeElementWrapper) {
          final StructureViewTreeElement oldElement = (StructureViewTreeElement)oldInstance.getValue();
          if (newElement.getValue() != null) {
            if (Comparing.equal(newElement.getValue(), oldElement.getValue())) return i;
          }
        }
      } else {
        if (newInstance.equals(oldInstance)) return i;
      }
    }
    return -1;
  }

  protected abstract void copyFromNewInstance(final CachingChildrenTreeNode newInstance);

  protected abstract void performTreeActions();

  protected abstract void initChildren();

  public void navigate(final boolean requestFocus) {
    ((Navigatable)getValue()).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return getValue() instanceof Navigatable && ((Navigatable)getValue()).canNavigate();
  }

  public boolean canNavigateToSource() {
    return getValue() instanceof Navigatable && ((Navigatable)getValue()).canNavigateToSource();
  }

  protected void clearChildren() {
    if (myChildren != null) {
      myChildren.clear();
    } else {
      myChildren = new ArrayList<CachingChildrenTreeNode>();
    }
  }

  public void rebuildChildren() {
    if (myChildren != null) {
      myOldChildren = myChildren;
      for (final CachingChildrenTreeNode node : myChildren) {
        node.rebuildChildren();
      }
      myChildren = null;
    }
  }


}
