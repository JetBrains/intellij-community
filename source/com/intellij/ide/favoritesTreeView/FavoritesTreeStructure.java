package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.IdeBundle;
import com.intellij.j2ee.module.components.J2EEModuleUrl;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeStructure extends ProjectAbstractTreeStructureBase implements JDOMExternalizable {
  private static final ArrayList<AbstractUrl> ourAbstractUrlProviders = new ArrayList<AbstractUrl>();
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.favoritesTreeView.FavoritesTreeStructure");
  static {
    ourAbstractUrlProviders.add(new ClassUrl(null, null));
    ourAbstractUrlProviders.add(new ModuleUrl(null, null));
    ourAbstractUrlProviders.add(new DirectoryUrl(null, null));
    ourAbstractUrlProviders.add(new PackageUrl(null, null));

    ourAbstractUrlProviders.add(new ModuleGroupUrl(null));
    ourAbstractUrlProviders.add(new FormUrl(null, null));
    ourAbstractUrlProviders.add(new ResourceBundleUrl(null));

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
  @NonNls private static final String CLASS_NAME = "klass";
  @NonNls private static final String FAVORITES_ROOT = "favorite_root";
  @NonNls private static final String ATTRIBUTE_TYPE = "type";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String ATTRIBUTE_MODULE = "module";

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

  public void addToFavorites(AbstractTreeNode treeNode) {
    Object elementToAdd = treeNode.getValue() instanceof SmartPsiElementPointer ? ((SmartPsiElementPointer)treeNode.getValue()).getElement() : treeNode.getValue();
    for (AbstractTreeNode node : myFavorites) {
      Object element = node.getValue() instanceof SmartPsiElementPointer ? ((SmartPsiElementPointer)node.getValue()).getElement() : node.getValue();
      if (Comparing.equal(element, elementToAdd)) return;
    }
    myFavorites.add(treeNode);
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
        Set<AbstractTreeNode> result = new HashSet<AbstractTreeNode>();
        for (Iterator<AbstractTreeNode> iterator = myFavorites.iterator(); iterator.hasNext();) {
          AbstractTreeNode abstractTreeNode = iterator.next();
          final Object val = abstractTreeNode.getValue();
          if (val != null){
            if (val instanceof PsiElement && !((PsiElement)val).isValid()){
              continue;
            }
            if (val instanceof SmartPsiElementPointer &&
               (((SmartPsiElementPointer)val).getElement() == null )){
              continue;
            }
            if (val instanceof Form){
              final Collection<AbstractTreeNode> children = abstractTreeNode.getChildren();
              boolean toContinue = false;
              for (AbstractTreeNode node : children) {
                final Object value = node.getValue();
                if (!(value instanceof PsiElement) || !((PsiElement)value).isValid()){
                  toContinue = true;
                  break;
                }
              }
              if (toContinue) continue;
            }
            if (val instanceof ResourceBundle){
              ResourceBundle resourceBundle = (ResourceBundle)val;
              List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles(myProject);
              if (propertiesFiles.size() == 1){
                result.add(new PsiFileNode(myProject, propertiesFiles.iterator().next(), getFavoritesConfiguration()));
                continue;
              }
            }
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
    return new AbstractTreeNode<String>(myProject, IdeBundle.message("favorites.empty.screen")){
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
    if (element instanceof AbstractTreeNode) {
      parent = ((AbstractTreeNode)element).getParent();
    }
    if (parent == null) {
      return myRoot;
    }
    return parent;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return new FavoritesTreeNodeDescriptor(myProject, parentDescriptor, (AbstractTreeNode)element);
  }

  public void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  public boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  public void removeFromFavorites(final AbstractTreeNode element) {
    myFavorites.remove(element);
  }

  public boolean contains(final VirtualFile vFile){
    LOG.assertTrue(vFile != null);
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
    final Object[] childElements = getChildElements(myRoot);
    for (Object obj : childElements) {
      AbstractTreeNode node = (AbstractTreeNode)obj;
      if (node.getValue() instanceof SmartPsiElementPointer) {
        final VirtualFile virtualFile = BasePsiNode.getVirtualFile(((SmartPsiElementPointer)node.getValue()).getElement());
        if (virtualFile == null) continue;
        if (vFile.getPath().equals(virtualFile.getPath())) {
          return true;
        }
        if (!virtualFile.isDirectory()) {
          continue;
        }
        projectFileIndex.iterateContentUnderDirectory(virtualFile, contentIterator);
      }
      if (node.getValue() instanceof PackageElement) {
        final PackageElement packageElement = ((PackageElement)node.getValue());
        final PsiPackage aPackage = packageElement.getPackage();
        GlobalSearchScope scope = packageElement.getModule() != null ? GlobalSearchScope.moduleScope(packageElement.getModule()) : GlobalSearchScope.projectScope(myProject);
        final PsiDirectory[] directories = aPackage.getDirectories(scope);
        for (PsiDirectory directory : directories) {
          projectFileIndex.iterateContentUnderDirectory(directory.getVirtualFile(), contentIterator);
        }
      }
      if (node.getValue() instanceof PsiElement) {
        final VirtualFile virtualFile = BasePsiNode.getVirtualFile(((PsiElement)node.getValue()));
        if (virtualFile == null) continue;
        if (vFile.getPath().equals(virtualFile.getPath())){
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
        for (PsiFile psiFile : forms) {
          final VirtualFile virtualFile = psiFile.getVirtualFile();
          if (virtualFile != null && virtualFile.equals(vFile)) {
            return true;
          }
        }
      }
      if (node.getValue() instanceof ModuleGroup){
        ModuleGroup group = (ModuleGroup) node.getValue();
        final Module[] modules = group.modulesInGroup(myProject, true);
        for (Module module : modules) {
          ModuleRootManager.getInstance(module).getFileIndex().iterateContent(contentIterator);
        }
      }
      if (node.getValue() instanceof ResourceBundle) {
        ResourceBundle bundle = (ResourceBundle)node.getValue();
        final List<PropertiesFile> propertiesFiles = bundle.getPropertiesFiles(myProject);
        for (PropertiesFile file : propertiesFiles) {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile == null) continue;
          if (vFile.getPath().equals(virtualFile.getPath())){
            return true;
          }
        }
      }
      if (!find.isEmpty()){
        return true;
      }
    }
    return false;
  }

  public void initFavoritesList() {
    for (AbstractUrl abstractUrl : myAbstractUrls.keySet()) {
      final Object[] path = abstractUrl.createPath(myProject);
      if (path == null || path.length < 1 || path[0] == null) {
        continue;
      }
      try {
        if (abstractUrl instanceof FormUrl) {
          final PsiManager psiManager = PsiManager.getInstance(myProject);
          myFavorites.add(FormNode.constructFormNode(psiManager, (PsiClass)path[0], myProject, myFavoritesConfiguration));
        }
        else {
          myFavorites
            .add(
              ProjectViewNode.createTreeNode((Class<? extends AbstractTreeNode>)Class.forName(myAbstractUrls.get(abstractUrl)), myProject,
                                             path[path.length - 1],
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

  private static @Nullable AbstractUrl createUrlByElement(Object element) {
    for (Iterator<AbstractUrl> iterator = ourAbstractUrlProviders.iterator(); iterator.hasNext();) {
      AbstractUrl urlProvider = iterator.next();
      AbstractUrl url = urlProvider.createUrlByElement(element);
      if (url != null) return url;
    }
    return null;
  }

  @Nullable
  private static AbstractUrl readUrlFromElement(Element element) {
    final String type = element.getAttributeValue(ATTRIBUTE_TYPE);
    final String urlValue = element.getAttributeValue(ATTRIBUTE_URL);
    final String moduleName = element.getAttributeValue(ATTRIBUTE_MODULE);
    for (int i = 0; i < ourAbstractUrlProviders.size(); i++) {
      AbstractUrl urlProvider = ourAbstractUrlProviders.get(i);
      AbstractUrl url = urlProvider.checkMyUrl(type, moduleName, urlValue);
      if (url != null) return url;
    }
    return null;
  }


  public void readExternal(Element element) throws InvalidDataException {
    myAbstractUrls.clear();
    for (Iterator<Element> iterator = element.getChildren(FAVORITES_ROOT).iterator(); iterator.hasNext();) {
      Element favorite = iterator.next();
      final String klass = favorite.getAttributeValue(CLASS_NAME);
      final AbstractUrl abstractUrl = readUrlFromElement(favorite);
      if (abstractUrl != null) {
        myAbstractUrls.put(abstractUrl, klass);
      }
    }
    myFavoritesConfiguration.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Object[] favorites = getChildElements(myRoot);
    for (Object favoriteElement : favorites) {
      final AbstractTreeNode favoritesTreeElement = (AbstractTreeNode)favoriteElement;
      final Element favorite = new Element(FAVORITES_ROOT);
      final Object value = favoritesTreeElement.getValue();
      if (value instanceof String ){ //favorites are empty
        continue;
      }
      final AbstractUrl url = createUrlByElement(value instanceof SmartPsiElementPointer ? ((SmartPsiElementPointer)value).getElement() : value);
      if (url != null) {
        url.write(favorite);
        favorite.setAttribute(CLASS_NAME, favoritesTreeElement.getClass().getName());
        element.addContent(favorite);
      }
    }
    myFavoritesConfiguration.writeExternal(element);
  }

}
