package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.projectView.impl.nodes.*;
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

import java.util.*;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeStructure extends ProjectAbstractTreeStructureBase implements JDOMExternalizable {
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

  private final AbstractTreeNode<String> myRoot;

  private Set<AbstractTreeNode> myFavorites = new HashSet<AbstractTreeNode>();
  private HashMap<AbstractUrl, String> myAbstractUrls = new HashMap<AbstractUrl, String>();
  private FavoritesTreeViewConfiguration myFavoritesConfiguration = new FavoritesTreeViewConfiguration();
  public FavoritesTreeStructure(Project project) {
    super(project);
    myRoot = new AbstractTreeNode<String>(myProject, "") {
      public Collection<AbstractTreeNode> getChildren() {
        return myFavorites;
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

  //for tests only
  public Set<AbstractTreeNode> getFavorites() {
    return myFavorites;
  }

  public Object[] getChildElements(Object element) {
    if (!(element instanceof AbstractTreeNode)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final AbstractTreeNode favoritesTreeElement = (AbstractTreeNode)element;
    try {
      if (element == myRoot) {
        //todo
        Set<AbstractTreeNode> result = new HashSet<AbstractTreeNode>();
        for (Iterator<AbstractTreeNode> iterator = myFavorites.iterator(); iterator.hasNext();) {
          AbstractTreeNode abstractTreeNode = iterator.next();
          final Object val = abstractTreeNode.getValue();
          if (val != null && (!(val instanceof PsiElement) || ((PsiElement)val).isValid())){
            result.add(abstractTreeNode);
          }
        }
        myFavorites = result;
        if (myFavorites.isEmpty()) {
          return new Object[]{getEmptyScreen()};
        }
        return myFavorites.toArray(new Object[myFavorites.size()]);
      }
      return super.getChildElements(favoritesTreeElement);
    }
    catch (Exception e) {
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private AbstractTreeNode<String> getEmptyScreen() {
    return new AbstractTreeNode<String>(myProject, "There is nothing to display. Add node to favorites list."){
                                public Collection<AbstractTreeNode> getChildren() {
                                  return null;
                                }

                                public void update(final PresentationData presentation) {
                                  presentation.setPresentableText(getValue());
                                }
                              };
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

  public boolean contains(final VirtualFile vFile){
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final Set<Boolean> find = new HashSet<Boolean>();
    final ContentIterator contentIterator = new ContentIterator() {
      public boolean processFile(VirtualFile fileOrDir) {
        if (fileOrDir == null ? vFile == null : fileOrDir.getPath().equals(vFile.getPath())) {
          find.add(Boolean.TRUE);
        }
        return true;
      }
    };
    for (Iterator<AbstractTreeNode> iterator = myRoot.getChildren().iterator(); iterator.hasNext();) {
      AbstractTreeNode node = iterator.next();
      if (node.getValue() instanceof PsiElement){
        final VirtualFile virtualFile = BasePsiNode.getVirtualFile(((PsiElement)node.getValue()));
        if (vFile == null ? virtualFile == null : vFile.getPath().equals(virtualFile.getPath())){
          return true;
        }
        if (!virtualFile.isDirectory()){
          continue;
        }
        projectFileIndex.iterateContentUnderDirectory(virtualFile, contentIterator);
      }
      if (node.getValue() instanceof Module){
        ModuleRootManager.getInstance(((Module)node.getValue())).getFileIndex().iterateContent(contentIterator);
      }
      if (node.getValue() instanceof LibraryGroupElement){
        final boolean inLibrary =
          ModuleRootManager.getInstance(((LibraryGroupElement)node.getValue()).getModule()).getFileIndex().isInContent(vFile) &&
          projectFileIndex.isInLibraryClasses(vFile);
        if (inLibrary){
          return true;
        }
      }
      if (node.getValue() instanceof NamedLibraryElement){
        NamedLibraryElement namedLibraryElement = (NamedLibraryElement)node.getValue();
        final VirtualFile[] files = namedLibraryElement.getOrderEntry().getFiles(OrderRootType.CLASSES);
        if (files != null && ArrayUtil.find(files, vFile) > -1){
          return true;
        }
      }
      if (node.getValue() instanceof Form){
        Form form = (Form) node.getValue();
        PsiFile[] forms = form.getClassToBind().getManager().getSearchHelper().findFormsBoundToClass(form.getClassToBind().getQualifiedName());
        for (int i = 0; i < forms.length; i++) {
          PsiFile psiFile = forms[i];
          final VirtualFile virtualFile = psiFile.getVirtualFile();
          if (virtualFile == null ? vFile == null : virtualFile.equals(vFile)){
            return true;
          }
        }
      }
      if (node.getValue() instanceof ModuleGroup){
        ModuleGroup group = (ModuleGroup) node.getValue();
        final Module[] modules = group.modulesInGroup(myProject, true);
        for (int i = 0; i < modules.length; i++) {
          Module module = modules[i];
          ModuleRootManager.getInstance(module).getFileIndex().iterateContent(contentIterator);
        }
      }
      if (!find.isEmpty()){
        return true;
      }
    }
    return false;
  }

  public void initFavoritesList() {
    for (Iterator<AbstractUrl> iterator = myAbstractUrls.keySet().iterator(); iterator.hasNext();) {
      AbstractUrl abstractUrl = iterator.next();
      final Object[] path = abstractUrl.createPath(myProject);
      if (path == null || path.length < 1){
        continue;
      }
      try {
        if (abstractUrl instanceof FormUrl){
          final PsiManager psiManager = PsiManager.getInstance(myProject);
          myFavorites.add(FormNode.constructFormNode(psiManager, (PsiClass)path[0], myProject, myFavoritesConfiguration));
        } else {
          myFavorites.add(ProjectViewNode.createTreeNode(Class.forName(myAbstractUrls.get(abstractUrl)), myProject, path[path.length - 1],
                                                         myFavoritesConfiguration));
        }
      }
      catch (Exception e) {
      }
    }
  }

  public FavoritesTreeViewConfiguration getFavoritesConfiguration() {
    return myFavoritesConfiguration;
  }

  private static AbstractUrl createUrlByElement(Object element) {
    for (Iterator<AbstractUrl> iterator = ourAbstractUrlProviders.iterator(); iterator.hasNext();) {
      AbstractUrl urlProvider = iterator.next();
      AbstractUrl url = urlProvider.createUrlByElement(element);
      if (url != null) return url;
    }
    return null;
  }

  private static AbstractUrl readUrlFromElement(Element element) {
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
    myAbstractUrls.clear();
    for (Iterator<Element> iterator = element.getChildren("favorite_root").iterator(); iterator.hasNext();) {
      Element favorite = iterator.next();
      final String klass = favorite.getAttributeValue("klass");
      final AbstractUrl abstractUrl = readUrlFromElement(favorite);
      if (abstractUrl != null) {
        myAbstractUrls.put(abstractUrl, klass);
      }
    }
    myFavoritesConfiguration.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator<AbstractTreeNode> iterator = myFavorites.iterator(); iterator.hasNext();) {
      AbstractTreeNode favoritesTreeElement = iterator.next();
      Element favorite = new Element("favorite_root");
      createUrlByElement(favoritesTreeElement.getValue()).write(favorite);
      favorite.setAttribute("klass", favoritesTreeElement.getClass().getName());
      element.addContent(favorite);
    }
    myFavoritesConfiguration.writeExternal(element);
  }

}
