package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
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
  private Project myProject;
  private Map<String, Content> myName2FavoritesListSet = new HashMap<String, Content>();
  public String myCurrentFavoritesList;
  private Map<String, AddToFavoritesAction> myActions = new HashMap<String, AddToFavoritesAction>();
  public static FavoritesViewImpl getInstance(Project project) {
    return project.getComponent(FavoritesViewImpl.class);
  }

  public FavoritesViewImpl(Project project, ProjectManager projectManager) {
    super(new TabbedPaneContentUI(), true, project, projectManager);
    myProject = project;
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
    SelectInManager selectInManager = SelectInManager.getInstance(myProject);
    selectInManager.addTarget(createSelectInTarget());
  }

  private SelectInTarget createSelectInTarget() {
    return new FavoritesViewSelectInTarget(myProject);
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.FAVORITES_VIEW, getComponent(), ToolWindowAnchor.RIGHT);
        toolWindow.setIcon(IconLoader.getIcon("/general/favorites.png"));
        new ContentManagerWatcher(toolWindow, FavoritesViewImpl.this);
        final ContentFactory contentFactory = PeerFactory.getInstance().getContentFactory();
        final DefaultActionGroup favoritesActionsGroup = ((DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.ADD_TO_FAVORITES));
        favoritesActionsGroup.removeAll();        
        if (myName2FavoritesListSet.isEmpty()){
          final FavoritesTreeViewPanel panel = new FavoritesTreeViewPanel(myProject, null, myProject.getName());
          final Content favoritesContent = contentFactory.createContent(panel, myProject.getName(), false);
          addContent(favoritesContent);
          final String key = myProject.getName();
          myName2FavoritesListSet.put(key, favoritesContent);
          myCurrentFavoritesList = key;
          panel.getFavoritesTreeStructure().initFavoritesList();
          final AddToFavoritesAction addAction = new AddToFavoritesAction(key);
          myActions.put(key, addAction);
          favoritesActionsGroup.add(addAction);
        } else {
          for (Iterator<String> iterator = myName2FavoritesListSet.keySet().iterator(); iterator.hasNext();) {
            final String key = iterator.next();
            final AddToFavoritesAction addAction = new AddToFavoritesAction(key);
            myActions.put(key, addAction);
            favoritesActionsGroup.add(addAction);
            final Content content = myName2FavoritesListSet.get(key);
            addContent(content);
            ((FavoritesTreeViewPanel)content.getComponent()).getFavoritesTreeStructure().initFavoritesList();
            if (key.equals(myCurrentFavoritesList)){
              setSelectedContent(content);
            }
          }
        }
        favoritesActionsGroup.addSeparator();
        favoritesActionsGroup.add(new AddToNewFavoritesListAction());
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
    final FavoritesTreeViewPanel panel = new FavoritesTreeViewPanel(myProject, null, name);
    final Content favoritesContent = contentFactory.createContent(panel, name, false);
    addContent(favoritesContent);
    myName2FavoritesListSet.put(name, favoritesContent);
    final AddToFavoritesAction addAction = new AddToFavoritesAction(name);
    myActions.put(name, addAction);
    ((DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.ADD_TO_FAVORITES)).add(addAction);
    return panel;
  }

  public void removeCurrentFavoritesList(){
    ((DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.ADD_TO_FAVORITES)).remove(myActions.remove(myCurrentFavoritesList));
    final Content content = myName2FavoritesListSet.remove(myCurrentFavoritesList);
    removeContent(content);
  }

  public String [] getAllAddActionNamesButThis(){
    final Set<String > temp = new HashSet<String >(myActions.keySet());
    temp.remove(myCurrentFavoritesList);
    return temp.isEmpty() ? null : temp.toArray(new String[temp.size()]);
  }

  public String [] getAllAddActionNames(){
    return myActions.keySet().toArray(new String[myActions.keySet().size()]);
  } 

  public AddToFavoritesAction getAddToFavoritesAction(String name){
    return myActions.get(name);
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.FAVORITES_VIEW);
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
      final FavoritesTreeViewPanel favoritesPanel = new FavoritesTreeViewPanel(myProject, null, name);
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
}
