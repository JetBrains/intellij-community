package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractUrl;
import com.intellij.ide.projectView.impl.ProjectTreeStructure;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeStructure extends ProjectTreeStructure {
  private final FavoritesManager myFavoritesManager;
  private final String myListName;

  public FavoritesTreeStructure(Project project, @NotNull final String name) {
    super(project, FavoritesProjectViewPane.ID);
    myListName = name;
    myFavoritesManager = FavoritesManager.getInstance(project);
  }

  protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
    return new AbstractTreeNode<String>(myProject, "") {
      @NotNull
      public Collection<AbstractTreeNode> getChildren() {
        return getFavoritesRoots();
      }

      public void update(final PresentationData presentation) {
      }
    };
  }

  public void addToFavorites(AbstractTreeNode treeNode) {
    //Object elementToAdd = treeNode.getValue() instanceof SmartPsiElementPointer ? ((SmartPsiElementPointer)treeNode.getValue()).getElement() : treeNode.getValue();
    //for (AbstractTreeNode node : myFavoritesRoots) {
    //  Object element = node.getValue() instanceof SmartPsiElementPointer ? ((SmartPsiElementPointer)node.getValue()).getElement() : node.getValue();
    //  if (Comparing.equal(element, elementToAdd)) return;
    //}
    //myFavoritesRoots.add(treeNode);
  }

  //for tests only
  @NotNull public Collection<AbstractTreeNode> getFavoritesRoots() {
    List<Pair<AbstractUrl, String>> urls = myFavoritesManager.getFavoritesListRootUrls(myListName);
    if (urls == null) return Collections.emptyList();
    return createFavoritesRoots(urls);
  }

  public Object[] getChildElements(Object element) {
    if (!(element instanceof AbstractTreeNode)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final AbstractTreeNode favoritesTreeElement = (AbstractTreeNode)element;
    try {
      if (element != getRootElement()) {
        return super.getChildElements(favoritesTreeElement);
      }
      Set<AbstractTreeNode> result = new HashSet<AbstractTreeNode>();
      for (AbstractTreeNode<?> abstractTreeNode : getFavoritesRoots()) {
        final Object val = abstractTreeNode.getValue();
        if (val == null) {
          continue;
        }
        if (val instanceof PsiElement && !((PsiElement)val).isValid()) {
          continue;
        }
        if (val instanceof SmartPsiElementPointer && ((SmartPsiElementPointer)val).getElement() == null) {
          continue;
        }
        if (val instanceof ResourceBundle) {
          ResourceBundle resourceBundle = (ResourceBundle)val;
          List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles(myProject);
          if (propertiesFiles.size() == 1) {
            result.add(new PsiFileNode(myProject, propertiesFiles.iterator().next(), this));
            continue;
          }
        }

        boolean isInvalid = false;
        for(FavoriteNodeProvider nodeProvider: ApplicationManager.getApplication().getComponents(FavoriteNodeProvider.class)) {
          if (nodeProvider.isInvalidElement(val)) {
            isInvalid = true;
            break;
          }
        }
        if (isInvalid) continue;

        result.add(abstractTreeNode);
      }
      //myFavoritesRoots = result;
      if (result.isEmpty()) {
        result.add(getEmptyScreen());
      }
      return result.toArray(new Object[result.size()]);
    }
    catch (Exception e) {
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private AbstractTreeNode<String> getEmptyScreen() {
    return new AbstractTreeNode<String>(myProject, IdeBundle.message("favorites.empty.screen")) {
      @NotNull
      public Collection<AbstractTreeNode> getChildren() {
        return Collections.emptyList();
      }

      public void update(final PresentationData presentation) {
        presentation.setPresentableText(getValue());
      }
    };
  }

  public Object getParentElement(Object element) {
    AbstractTreeNode parent = null;
    if (element == getRootElement()) {
      return null;
    }
    if (element instanceof AbstractTreeNode) {
      parent = ((AbstractTreeNode)element).getParent();
    }
    if (parent == null) {
      return getRootElement();
    }
    return parent;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return new FavoritesTreeNodeDescriptor(myProject, parentDescriptor, (AbstractTreeNode)element);
  }

  @NotNull private Collection<AbstractTreeNode> createFavoritesRoots(@NotNull List<Pair<AbstractUrl,String>> urls) {
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (Pair<AbstractUrl, String> pair : urls) {
      AbstractUrl abstractUrl = pair.getFirst();
      final Object[] path = abstractUrl.createPath(myProject);
      if (path == null || path.length < 1 || path[0] == null) {
        continue;
      }
      try {
        String className = pair.getSecond();
        Class<? extends AbstractTreeNode> nodeClass = (Class<? extends AbstractTreeNode>)Class.forName(className);
        AbstractTreeNode node = ProjectViewNode.createTreeNode(nodeClass, myProject, path[path.length - 1], this);
        result.add(node);
      }
      catch (Exception e) {
      }
    }
    return result;
  }
}
