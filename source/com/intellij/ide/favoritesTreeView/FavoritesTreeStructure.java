package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.projectView.impl.nodes.FormNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.j2ee.module.components.J2EEModuleUrl;
import com.intellij.j2ee.module.view.ejb.CmpFieldUrl;
import com.intellij.j2ee.module.view.ejb.CmrFieldUrl;
import com.intellij.j2ee.module.view.ejb.EjbClassUrl;
import com.intellij.j2ee.module.view.ejb.EjbUrl;
import com.intellij.j2ee.module.view.web.FilterUrl;
import com.intellij.j2ee.module.view.web.ListenerUrl;
import com.intellij.j2ee.module.view.web.ServletUrl;
import com.intellij.j2ee.module.view.web.WebRootFileUrl;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

import java.util.*;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeStructure extends ProjectAbstractTreeStructureBase implements ProjectComponent, JDOMExternalizable {
  private static final ArrayList<AbstractUrl> ourAbstractUrlProviders = new ArrayList<AbstractUrl>();

  static {
    ourAbstractUrlProviders.add(new ClassUrl(null, null));
    ourAbstractUrlProviders.add(new ModuleUrl(null, null));
    ourAbstractUrlProviders.add(new DirectoryUrl(null, null));
    ourAbstractUrlProviders.add(new PackageUrl(null, null));

    ourAbstractUrlProviders.add(new ModuleGroupUrl(null));
    ourAbstractUrlProviders.add(new FormUrl(null, null));

    ourAbstractUrlProviders.add(new EjbUrl(null, null));
    ourAbstractUrlProviders.add(new EjbClassUrl(null, null));
    ourAbstractUrlProviders.add(new CmpFieldUrl(null, null));
    ourAbstractUrlProviders.add(new CmrFieldUrl(null, null));

    ourAbstractUrlProviders.add(new ServletUrl(null, null));
    ourAbstractUrlProviders.add(new FilterUrl(null, null));
    ourAbstractUrlProviders.add(new ListenerUrl(null, null));
    ourAbstractUrlProviders.add(new WebRootFileUrl(null, null));

    ourAbstractUrlProviders.add(new J2EEModuleUrl(null));

    ourAbstractUrlProviders.add(new PsiFileUrl(null, null));
    ourAbstractUrlProviders.add(new LibraryModuleGroupUrl(null));
    ourAbstractUrlProviders.add(new NamedLibraryUrl(null, null));
    ourAbstractUrlProviders.add(new FieldUrl(null, null));
    ourAbstractUrlProviders.add(new MethodUrl(null, null));
  }

  private final AbstractTreeNode myRoot;

  private Set<AbstractTreeNode> myFavorites = new HashSet<AbstractTreeNode>();
  private HashMap<AbstractUrl, String> myAbstractUrls = new HashMap<AbstractUrl, String>();

  public FavoritesTreeStructure(Project project) {
    super(project);
    myRoot = new AbstractTreeNode(myProject, "Root") {
      public Collection<AbstractTreeNode> getChildren() {
        return null;
      }

      public void update(final PresentationData presentation) {
      }
    };
  }

  public Object getRootElement() {
    return myRoot;
  }

  public void addToFavorites(AbstractTreeNode element) {
    myFavorites.add(element);
  }

  public Object[] getChildElements(Object element) {
    if (!(element instanceof AbstractTreeNode)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final AbstractTreeNode favoritesTreeElement = (AbstractTreeNode)element;
    try {
      if (element == myRoot) {
        return myFavorites.toArray(new Object[myFavorites.size()]);
      }
      return super.getChildElements(favoritesTreeElement);
    }
    catch (Exception e) {
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public Object getParentElement(Object element) {
    AbstractTreeNode parent = null;
    if (element instanceof AbstractTreeNode) {
      parent = ((AbstractTreeNode)element).getParent();
    }
    if (parent == null) {
      return myRoot;
    }
    return parent;
  }

  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return new FavoritesTreeNodeDescriptor(myProject, parentDescriptor, (AbstractTreeNode)element);
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  public void removeFromFavorites(final AbstractTreeNode element) {
    myFavorites.remove(element);
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        final ViewSettings favoritesConfig = FavoritesTreeViewConfiguration.getInstance(myProject);
        for (Iterator<AbstractUrl> iterator = myAbstractUrls.keySet().iterator(); iterator.hasNext();) {
          AbstractUrl abstractUrl = iterator.next();
          final Object[] path = abstractUrl.createPath(myProject);
          try {
            if (abstractUrl instanceof FormUrl){
              final PsiManager psiManager = PsiManager.getInstance(myProject);
              myFavorites.add(FormNode.constructFormNode(psiManager, (PsiClass)path[0], myProject, favoritesConfig));
            } else {
              myFavorites.add(ProjectViewNode.createTreeNode(Class.forName(myAbstractUrls.get(abstractUrl)), myProject, path[path.length - 1],
                                                             favoritesConfig));
            }
          }
          catch (Exception e) {
          }
        }
      }
    });
  }

  public void projectClosed() {

  }

  public String getComponentName() {
    return "FavoritesTreeStructure";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  private AbstractUrl createUrlByElement(Object element) {
    for (Iterator<AbstractUrl> iterator = ourAbstractUrlProviders.iterator(); iterator.hasNext();) {
      AbstractUrl urlProvider = iterator.next();
      AbstractUrl url = urlProvider.createUrlByElement(element);
      if (url != null) return url;
    }
    return null;
  }

  private AbstractUrl readUrlFromElement(Element element) {
    final String type = element.getAttributeValue("type");
    final String urlValue = element.getAttributeValue("url");
    final String moduleName = element.getAttributeValue("module");
    for (int i = 0; i < ourAbstractUrlProviders.size(); i++) {
      AbstractUrl urlProvider = ourAbstractUrlProviders.get(i);
      AbstractUrl url = urlProvider.checkMyUrl(type, moduleName, urlValue);
      if (url != null) return url;
    }
    return null;
  }


  public void readExternal(Element element) throws InvalidDataException {
    for (Iterator<Element> iterator = element.getChildren("favorite_root").iterator(); iterator.hasNext();) {
      Element favorite = iterator.next();
      final String klass = favorite.getAttributeValue("klass");

      final AbstractUrl abstractUrl = readUrlFromElement(favorite);
      myAbstractUrls.put(abstractUrl, klass);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator<AbstractTreeNode> iterator = myFavorites.iterator(); iterator.hasNext();) {
      AbstractTreeNode favoritesTreeElement = iterator.next();
      Element favorite = new Element("favorite_root");
      createUrlByElement(favoritesTreeElement.getValue()).write(favorite);
      favorite.setAttribute("klass", favoritesTreeElement.getClass().getName());
      element.addContent(favorite);
    }
  }

  public static FavoritesTreeStructure getInstance(final Project project) {
    return project.getComponent(FavoritesTreeStructure.class);
  }


}
