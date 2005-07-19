package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.*;
import com.intellij.ui.content.impl.ContentManagerImpl;
import org.jdom.Element;

import java.util.*;

public class FavoritesViewImpl extends ContentManagerImpl implements ProjectComponent, JDOMExternalizable {
  private Map<String, Content> myName2FavoritesListSet = new HashMap<String, Content>();
  public String myCurrentFavoritesList;
  private Map<String, AddToFavoritesAction> myActions = new HashMap<String, AddToFavoritesAction>();
  private Project myCurrentProject;
  public static FavoritesViewImpl getInstance(Project project) {
    return project.getComponent(FavoritesViewImpl.class);
  }

  public FavoritesViewImpl(Project project, ProjectManager projectManager) {
    super(new TabbedPaneContentUI(), false, project, projectManager);
    myCurrentProject = project;
    addContentManagerListener(new ContentManagerListener() {
      public void contentAdded(ContentManagerEvent event) {
      }

      public void contentRemoved(ContentManagerEvent event) {
      }

      public void contentRemoveQuery(ContentManagerEvent event) {
      }

      public void selectionChanged(ContentManagerEvent event) {
        final Content content = event.getContent();
        if (content == null){
          return;
        }
        FavoritesTreeViewPanel currentPane = (FavoritesTreeViewPanel)content.getComponent();
        if (currentPane != null){
          myCurrentFavoritesList = currentPane.getName();
        }
      }
    });
    SelectInManager selectInManager = SelectInManager.getInstance(myCurrentProject);
    selectInManager.addTarget(createSelectInTarget());
  }

  private SelectInTarget createSelectInTarget() {
    return new FavoritesViewSelectInTarget(myCurrentProject);
  }

  public void initComponent() {
  }

  public void disposeComponent() {}

  public void projectOpened() {
    StartupManager.getInstance(myCurrentProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myCurrentProject);
        ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.FAVORITES_VIEW, getComponent(), ToolWindowAnchor.RIGHT);
        toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowFavorites.png"));
        new ContentManagerWatcher(toolWindow, FavoritesViewImpl.this);
        final ContentFactory contentFactory = PeerFactory.getInstance().getContentFactory();
        if (myName2FavoritesListSet.isEmpty()){
          final FavoritesTreeViewPanel panel = new FavoritesTreeViewPanel(myCurrentProject, null, myCurrentProject.getName());
          final Content favoritesContent = contentFactory.createContent(panel, myCurrentProject.getName(), false);
          addContent(favoritesContent);
          final String key = myCurrentProject.getName();
          myName2FavoritesListSet.put(key, favoritesContent);
          myCurrentFavoritesList = key;
          panel.getFavoritesTreeStructure().initFavoritesList();
          final AddToFavoritesAction addAction = new AddToFavoritesAction(key);
          myActions.put(key, addAction);
        } else {
          for (Iterator<String> iterator = myName2FavoritesListSet.keySet().iterator(); iterator.hasNext();) {
            final String key = iterator.next();
            final AddToFavoritesAction addAction = new AddToFavoritesAction(key);
            myActions.put(key, addAction);
            final Content content = myName2FavoritesListSet.get(key);
            addContent(content);
            ((FavoritesTreeViewPanel)content.getComponent()).getFavoritesTreeStructure().initFavoritesList();
            if (key.equals(myCurrentFavoritesList)){
              setSelectedContent(content);
            }
          }
        }
      }
    });
  }

  public FavoritesTreeViewPanel getFavoritesTreeViewPanel(String id) {
    return (FavoritesTreeViewPanel)myName2FavoritesListSet.get(id).getComponent();
  }

  public FavoritesTreeViewPanel getCurrentTreeViewPanel(){
    return (FavoritesTreeViewPanel)myName2FavoritesListSet.get(myCurrentFavoritesList).getComponent();
  }

  public String[] getAvailableFavoritesLists(){
    final Set<String> keys = myName2FavoritesListSet.keySet();
    return keys.toArray(new String[keys.size()]);
  }

  public FavoritesTreeViewPanel addNewFavoritesList(String name){
    final ContentFactory contentFactory = PeerFactory.getInstance().getContentFactory();
    final FavoritesTreeViewPanel panel = new FavoritesTreeViewPanel(myCurrentProject, null, name);
    final Content favoritesContent = contentFactory.createContent(panel, name, false);
    addContent(favoritesContent);
    myName2FavoritesListSet.put(name, favoritesContent);
    final AddToFavoritesAction addAction = new AddToFavoritesAction(name);
    myActions.put(name, addAction);
    return panel;
  }

  public boolean removeContent(Content content){
    final String name = content.getComponent().getName();
    myActions.remove(name);
    myName2FavoritesListSet.remove(name);
    return super.removeContent(content);
  }

  public String [] getAllAddActionNamesButThis(){
    final Set<String > temp = new HashSet<String >(myActions.keySet());
    temp.remove(myCurrentFavoritesList);
    return temp.isEmpty() ? null : temp.toArray(new String[temp.size()]);
  }

  public AddToFavoritesAction getAddToFavoritesAction(String name){
    return myActions.get(name);
  }

  public void projectClosed() {
    dispose();
    ToolWindowManager.getInstance(myCurrentProject).unregisterToolWindow(ToolWindowId.FAVORITES_VIEW);
  }

  private void dispose() {
    for (Iterator<String> iterator = myName2FavoritesListSet.keySet().iterator(); iterator.hasNext();) {
      final String key = iterator.next();
      final Content content = myName2FavoritesListSet.get(key);
      final FavoritesTreeViewPanel panel = (FavoritesTreeViewPanel)content.getComponent();
      panel.getBuilder().dispose();
    }
  }

  public String getComponentName() {
    return "FavoritesViewImpl";
  }

  public void readExternal(Element element) throws InvalidDataException {
    myName2FavoritesListSet.clear();
    for (Iterator<Element> iterator = element.getChildren("favorites_list").iterator(); iterator.hasNext();) {
      Element el = iterator.next();
      final String name = el.getAttributeValue("name");
      final ContentFactory contentFactory = PeerFactory.getInstance().getContentFactory();
      final FavoritesTreeViewPanel favoritesPanel = new FavoritesTreeViewPanel(myCurrentProject, null, name);
      myName2FavoritesListSet.put(name, contentFactory.createContent(favoritesPanel, name, false));
      favoritesPanel.getFavoritesTreeStructure().readExternal(el);
    }
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator<String> iterator = myName2FavoritesListSet.keySet().iterator(); iterator.hasNext();) {
      Element el = new Element("favorites_list");
      final String key = iterator.next();
      el.setAttribute("name", key);
      FavoritesTreeViewPanel favoritesTreeViewPanel = (FavoritesTreeViewPanel)myName2FavoritesListSet.get(key).getComponent();
      favoritesTreeViewPanel.getFavoritesTreeStructure().writeExternal(el);
      element.addContent(el);
    }
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean canCloseContents() {
    return getContentCount() > 1;
  }

  public String getCloseActionName() {
    return "Delete Favorites List";
  }

  public boolean canCloseAllContents() {
    return false;
  }

}
