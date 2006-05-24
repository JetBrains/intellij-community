/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.ui.customization.CustomizableActionsSchemas;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public abstract class AbstractProjectViewPane implements JDOMExternalizable, DataProvider, ProjectComponent {
  protected final Project myProject;
  protected Runnable myTreeChangeListener;
  protected JTree myTree;
  protected AbstractTreeStructure myTreeStructure;
  protected AbstractTreeBuilder myTreeBuilder;
  // subId->Tree state; key may be null
  private final Map<String,TreeState> myReadTreeState = new HashMap<String, TreeState>();
  private String mySubId;
  @NonNls private static final String ELEMENT_SUBPANE = "subPane";
  @NonNls private static final String ATTRIBUTE_SUBID = "subId";

  protected AbstractProjectViewPane(Project project) {
    myProject = project;
  }

  protected final void fireTreeChangeListener() {
    if (myTreeChangeListener != null) myTreeChangeListener.run();
  }

  public final void setTreeChangeListener(Runnable listener) {
    myTreeChangeListener = listener;
  }

  public final void removeTreeChangeListener() {
    myTreeChangeListener = null;
  }

  public abstract String getTitle();
  public abstract Icon getIcon();
  @NotNull public abstract String getId();
  @Nullable public final String getSubId(){
    return mySubId;
  }

  public final void setSubId(@Nullable String subId) {
    saveExpandedPaths();
    mySubId = subId;
  }

  /**
   * @return all supported sub views IDs.
   * should return empty array if there is no subViews as in Project/Packages view.
   */
  @NotNull public String[] getSubIds(){
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull public String getPresentableSubIdName(@NotNull final String subId) {
    throw new IllegalStateException("should not call");
  }
  public abstract JComponent createComponent();
  public JComponent getComponentToFocus() {
    return myTree;
  }
  public void expand(final Object[] path, final boolean requestFocus){
    if (myTreeBuilder == null || path == null) return;
    myTreeBuilder.buildNodeForPath(path);

    DefaultMutableTreeNode node = myTreeBuilder.getNodeForPath(path);
    if (node == null) {
      return;
    }
    TreePath treePath = new TreePath(node.getPath());
    myTree.expandPath(treePath);
    if (requestFocus) {
      myTree.requestFocus();
    }
    TreeUtil.selectPath(myTree, treePath);
  }
  public void expand(final Object element){
    myTreeBuilder.buildNodeForElement(element);
    DefaultMutableTreeNode node = myTreeBuilder.getNodeForElement(element);
    if (node == null) {
      return;
    }
    TreePath treePath = new TreePath(node.getPath());
    myTree.expandPath(treePath);
  }
  public void dispose() {
    if (myTreeBuilder != null) {
      myTreeBuilder.dispose();
      myTreeBuilder = null;
    }
    myTree = null;
    myTreeStructure = null;
  }

  public abstract void updateFromRoot(boolean restoreExpandedPaths);
  public abstract void select(Object element, VirtualFile file, boolean requestFocus);

  public TreePath[] getSelectionPaths() {
    return myTree == null ? null : myTree.getSelectionPaths();
  }

  public void addToolbarActions(DefaultActionGroup actionGroup) {
  }

  private List<AbstractTreeNode> getSelectedNodes(){
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return null;
    final ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        Object userObject = node.getUserObject();
        if (userObject instanceof AbstractTreeNode) {
          result.add((AbstractTreeNode)userObject);
        }
      }
    }
    return result;
  }

  public Object getData(String dataId) {
    if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
      TreePath[] paths = getSelectionPaths();
      if (paths == null) return null;
      final ArrayList<Navigatable> navigatables = new ArrayList<Navigatable>();
      for (TreePath path : paths) {
        Object lastPathComponent = path.getLastPathComponent();
        if (lastPathComponent instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
          Object userObject = node.getUserObject();
          if (userObject instanceof AbstractTreeNode) {
            navigatables.add((AbstractTreeNode)userObject);
          }
        }
      }
      if (navigatables.isEmpty()) {
        return null;
      }
      else {
        return navigatables.toArray(new Navigatable[navigatables.size()]);
      }
    }
    if (myTreeStructure instanceof AbstractTreeStructureBase) {
      return ((AbstractTreeStructureBase) myTreeStructure).getDataFromProviders(getSelectedNodes(), dataId);
    }
    return null;
  }

  // used for sorting tabs in the tabbed pane
  public abstract int getWeight();

  public abstract SelectInTarget createSelectInTarget();

  public final TreePath getSelectedPath() {
    final TreePath[] paths = getSelectionPaths();
    if (paths != null && paths.length == 1) return paths[0];
    return null;
  }

  public final NodeDescriptor getSelectedDescriptor() {
    final DefaultMutableTreeNode node = getSelectedNode();
    if (node == null) return null;
    Object userObject = node.getUserObject();
    if (userObject instanceof NodeDescriptor) {
      return (NodeDescriptor)userObject;
    }
    return null;
  }

  public final DefaultMutableTreeNode getSelectedNode() {
    TreePath path = getSelectedPath();
    if (path == null) {
      return null;
    }
    Object lastPathComponent = path.getLastPathComponent();
    if (!(lastPathComponent instanceof DefaultMutableTreeNode)) {
      return null;
    }
    return (DefaultMutableTreeNode)lastPathComponent;
  }

  public final Object getSelectedElement() {
    final Object[] elements = getSelectedElements();
    return elements.length == 1 ? elements[0] : null;
  }

  public final PsiElement[] getSelectedPSIElements() {
    List<PsiElement> psiElements = new ArrayList<PsiElement>();
    for (Object element : getSelectedElements()) {
      if (element instanceof PsiElement) {
        psiElements.add((PsiElement)element);
      }
      else if (element instanceof PackageElement) {
        PsiPackage aPackage = ((PackageElement)element).getPackage();
        if (aPackage != null) {
          psiElements.add(aPackage);
        }
      }
    }
    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  public final Object[] getSelectedElements() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return PsiElement.EMPTY_ARRAY;
    ArrayList<Object> list = new ArrayList<Object>(paths.length);
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        Object element = exhumeElementFromNode(node);
        if (element != null) {
          list.add(element);
        }
      }
    }
    return list.toArray(new Object[list.size()]);
  }

  protected Object exhumeElementFromNode(final DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    Object element = null;
    if (userObject instanceof AbstractTreeNode) {
      AbstractTreeNode descriptor = (AbstractTreeNode)userObject;
      element = descriptor.getValue();
    }
    else if (userObject instanceof NodeDescriptor) {
      NodeDescriptor descriptor = (NodeDescriptor)userObject;
      element = descriptor.getElement();
      if (element instanceof AbstractTreeNode) {
        element = ((AbstractTreeNode)element).getValue();
      }
    }
    else if (userObject != null) {
      element = userObject;
    }
    return element;
  }

  public AbstractTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  public void readExternal(Element element) throws InvalidDataException {
    List<Element> subPanes = element.getChildren(ELEMENT_SUBPANE);
    for (Element subPane : subPanes) {
      String subId = subPane.getAttributeValue(ATTRIBUTE_SUBID);
      TreeState treeState = new TreeState();
      treeState.readExternal(subPane);
      myReadTreeState.put(subId, treeState);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    saveExpandedPaths();
    for (String subId : myReadTreeState.keySet()) {
      TreeState treeState = myReadTreeState.get(subId);
      Element subPane = new Element(ELEMENT_SUBPANE);
      if (subId != null) {
        subPane.setAttribute(ATTRIBUTE_SUBID, subId);
      }
      treeState.writeExternal(subPane);
      element.addContent(subPane);
    }
  }

  void saveExpandedPaths() {
    if (myTree != null) {
      TreeState treeState = TreeState.createOn(myTree);
      myReadTreeState.put(getSubId(), treeState);
    }
  }

  public final void restoreExpandedPaths(){
    TreeState treeState = myReadTreeState.get(getSubId());
    if (treeState != null) {
      treeState.applyTo(myTree);
    }
  }

  public void installComparator() {
    final ProjectView projectView = ProjectView.getInstance(myProject);
    myTreeBuilder.setNodeDescriptorComparator(new GroupByTypeComparator() {
      protected boolean isSortByType() {
        return projectView.isSortByType(getId());
      }

      protected boolean isAbbreviatePackageNames() {
        return projectView.isAbbreviatePackageNames(getId());
      }
    });
  }

  protected void installTreePopupHandler(final String place, final String groupName) {
    if (ApplicationManager.getApplication() == null) return;
    PopupHandler popupHandler = new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionGroup group = (ActionGroup)CustomizableActionsSchemas.getInstance().getCorrectedAction(groupName);
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(place, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    };
    myTree.addMouseListener(popupHandler);
  }

}
