package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.Navigatable;
import gnu.trove.THashMap;

import java.util.*;

import org.jetbrains.annotations.NotNull;

public abstract class CachingChildrenTreeNode <Value> extends AbstractTreeNode<Value> {
  private List<CachingChildrenTreeNode> myChildren;
  protected List<CachingChildrenTreeNode> myOldChildren = null;
  protected final TreeModel myTreeModel;

  public CachingChildrenTreeNode(Project project, Value value, TreeModel treeModel) {
    super(project, value);
    myTreeModel = treeModel;
  }

  @NotNull
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
    Collection<AbstractTreeNode> children = getChildren();
    for (AbstractTreeNode child : children) {
      if (child instanceof GroupWrapper) {
        ((GroupWrapper)child).groupChildren(groupers);
      }
    }
  }

  private void groupElements(Grouper grouper) {
    ArrayList<AbstractTreeNode<TreeElement>> ungrouped = new ArrayList<AbstractTreeNode<TreeElement>>();
    Collection<AbstractTreeNode> children = getChildren();
    for (final AbstractTreeNode child : children) {
      CachingChildrenTreeNode<TreeElement> node = (CachingChildrenTreeNode<TreeElement>)child;
      if (node instanceof TreeElementWrapper) {
        ungrouped.add(node);
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
    Map<TreeElement,AbstractTreeNode> ungroupedObjects = collectValues(ungrouped);
    Collection<Group> groups = grouper.group(this, ungroupedObjects.keySet());

    Map<Group, GroupWrapper> groupNodes = createGroupNodes(groups);

    for (Group group : groups) {
      GroupWrapper groupWrapper = groupNodes.get(group);
      Collection<TreeElement> children = group.getChildren();
      for (TreeElement node : children) {
        CachingChildrenTreeNode child = new TreeElementWrapper(getProject(), node, myTreeModel);
        groupWrapper.addSubElement(child);
        AbstractTreeNode abstractTreeNode = ungroupedObjects.get(node);
        abstractTreeNode.setParent(groupWrapper);
      }
    }
  }

  private Map<TreeElement, AbstractTreeNode> collectValues(List<AbstractTreeNode<TreeElement>> ungrouped) {
    Map<TreeElement, AbstractTreeNode> objects = new LinkedHashMap<TreeElement, AbstractTreeNode>();
    for (final AbstractTreeNode<TreeElement> node : ungrouped) {
      objects.put(node.getValue(), node);
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
