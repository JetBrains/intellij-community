package com.intellij.ide.todo;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedPaneContentUI;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Vladimir Kondratyev
 */
public class TodoView implements ProjectComponent,JDOMExternalizable{
  private final Project myProject;
  private MyPropertyChangeListener myPropertyChangeListener;
  private MyFileTypeListener myFileTypeListener;

  private ContentManager myContentManager;
  private CurrentFileTodosPanel myCurrentFileTodos;
  private TodoPanel myAllTodos;

  private int mySelectedIndex;
  private TodoPanelSettings myCurrentPanelSettings;
  private TodoPanelSettings myAllPanelSettings;
  @NonNls private static final String ATTRIBUTE_SELECTED_INDEX = "selected-index";
  @NonNls private static final String ELEMENT_TODO_PANEL = "todo-panel";
  @NonNls private static final String ATTRIBUTE_ID = "id";
  @NonNls private static final String VALUE_SELECTED_FILE = "selected-file";
  @NonNls private static final String VALUE_ALL = "all";

  /* Invoked by reflection */
  TodoView(Project project){
    myProject=project;
    myCurrentPanelSettings=new TodoPanelSettings();
    myAllPanelSettings=new TodoPanelSettings();
  }

  public void readExternal(Element element) throws InvalidDataException{
    mySelectedIndex=0;
    try{
      mySelectedIndex=Integer.parseInt(element.getAttributeValue(ATTRIBUTE_SELECTED_INDEX));
    }catch(NumberFormatException ignored){
      //nothing to be done
    }

    //noinspection unchecked
    for (Element child : (Iterable<Element>)element.getChildren()) {
      if (ELEMENT_TODO_PANEL.equals(child.getName())) {
        String id = child.getAttributeValue(ATTRIBUTE_ID);
        if (VALUE_SELECTED_FILE.equals(id)) {
          myCurrentPanelSettings.readExternal(child);
        }
        else if (VALUE_ALL.equals(id)) {
          myAllPanelSettings.readExternal(child);
        }
        else {
          throw new IllegalArgumentException("unknown id: " + id);
        }
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException{
    if(myContentManager!=null){ // all panel were constructed
      Content content=myContentManager.getSelectedContent();
      element.setAttribute(ATTRIBUTE_SELECTED_INDEX,Integer.toString(myContentManager.getIndexOfContent(content)));
    }

    Element selectedFileElement=new Element(ELEMENT_TODO_PANEL);
    selectedFileElement.setAttribute(ATTRIBUTE_ID,VALUE_SELECTED_FILE);
    myCurrentPanelSettings.writeExternal(selectedFileElement);
    element.addContent(selectedFileElement);

    Element allElement=new Element(ELEMENT_TODO_PANEL);
    allElement.setAttribute(ATTRIBUTE_ID,VALUE_ALL);
    myAllPanelSettings.writeExternal(allElement);
    element.addContent(allElement);
  }

  public void disposeComponent(){}

  @NotNull
  public String getComponentName(){
    return "TodoView";
  }

  public void initComponent(){}

  public void projectClosed(){
    TodoConfiguration.getInstance().removePropertyChangeListener(myPropertyChangeListener);
    FileTypeManager.getInstance().removeFileTypeListener(myFileTypeListener);
    if(myAllTodos!=null){ // Panels can be null if project was closed before starup activities run
      myCurrentFileTodos.dispose();
      myAllTodos.dispose();
      ToolWindowManager toolWindowManager=ToolWindowManager.getInstance(myProject);
      toolWindowManager.unregisterToolWindow(ToolWindowId.TODO_VIEW);
    }
  }

  public void projectOpened(){
    myPropertyChangeListener=new MyPropertyChangeListener();
    TodoConfiguration.getInstance().addPropertyChangeListener(myPropertyChangeListener);

    myFileTypeListener=new MyFileTypeListener();
    FileTypeManager.getInstance().addFileTypeListener(myFileTypeListener);

    StartupManager startupManager=StartupManager.getInstance(myProject);
    // it causes building caches for TODOs
    startupManager.registerPostStartupActivity(
      new Runnable(){
        public void run(){
          // Create panels

          Content allTodosContent=PeerFactory.getInstance().getContentFactory().createContent(null, IdeBundle.message("title.project"),false);
          myAllTodos=new TodoPanel(myProject,myAllPanelSettings,false,allTodosContent){
            protected TodoTreeBuilder createTreeBuilder(JTree tree,DefaultTreeModel treeModel,Project project){
              AllTodosTreeBuilder builder=new AllTodosTreeBuilder(tree,treeModel,project);
              builder.init();
              return builder;
            }
          };
          allTodosContent.setComponent(myAllTodos);

          Content currentFileTodosContent=PeerFactory.getInstance().getContentFactory().createContent(null,IdeBundle.message("title.todo.current.file"),false);
          myCurrentFileTodos=new CurrentFileTodosPanel(myProject,myCurrentPanelSettings,currentFileTodosContent){
            protected TodoTreeBuilder createTreeBuilder(JTree tree,DefaultTreeModel treeModel,Project project){
              CurrentFileTodosTreeBuilder builder=new CurrentFileTodosTreeBuilder(tree,treeModel,project);
              builder.init();
              return builder;
            }
          };
          currentFileTodosContent.setComponent(myCurrentFileTodos);

          // Register tool window

          myContentManager=PeerFactory.getInstance().getContentFactory().createContentManager(new TabbedPaneContentUI(),false, myProject);
          myContentManager.addContent(allTodosContent);
          myContentManager.addContent(currentFileTodosContent);
          Content content=myContentManager.getContent(mySelectedIndex);
          myContentManager.setSelectedContent(content);
          ToolWindowManager toolWindowManager=ToolWindowManager.getInstance(myProject);
          ToolWindow toolWindow=toolWindowManager.registerToolWindow(
            ToolWindowId.TODO_VIEW,
            myContentManager.getComponent(),
            ToolWindowAnchor.BOTTOM
          );
          toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowTodo.png"));
          new TodoContentManagerWatcher(toolWindow,myContentManager);
        }
      }
    );
  }



  private final class MyPropertyChangeListener implements PropertyChangeListener{
    /**
     * If patterns have been changed the filtes can be touched also. But we have to update
     * filters after caches are rebuilded. Therefore if <code>myRebuildInProgress</code>
     * is <code>true</code> then it is deferred update of filters.
     */
    private boolean myRebuildInProgress;

    public void propertyChange(PropertyChangeEvent e){
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(e.getPropertyName())) {
        myRebuildInProgress = true;
        // this invokeLater guaranties that this code will be invoked after
        // PSI gets the same event.
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            // [vova] It's very important to pass null as project. Each TODO view shows own progress
            // window. It causes frame switching.
            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                  public void run() {
                    PsiSearchHelper searchHelper = PsiManager.getInstance(myProject).getSearchHelper();
                    searchHelper.findFilesWithTodoItems();
                  }
                });
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    updateFilters();
                    myRebuildInProgress = false;
                  }
                }, ModalityState.NON_MMODAL);
              }
            }, IdeBundle.message("progress.looking.for.todos"), false, myProject);
          }}, ModalityState.NON_MMODAL);
      }
      else if (TodoConfiguration.PROP_TODO_FILTERS.equals(e.getPropertyName())) {
        if (!myRebuildInProgress) {
          updateFilters();
        }
      }
    }

    private void updateFilters(){
      myCurrentFileTodos.updateTodoFilter();
      myAllTodos.updateTodoFilter();
    }
  }

  private final class MyFileTypeListener implements FileTypeListener{
    public void beforeFileTypesChanged(FileTypeEvent event) {
    }

    public void fileTypesChanged(FileTypeEvent e){
      // this invokeLater guaranties that this code will be invoked after
      // PSI gets the same event.
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;

          ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable(){
            public void run(){
              if (myAllTodos == null) return;

              ApplicationManager.getApplication().runReadAction(
                new Runnable(){
                  public void run(){
                    myAllTodos.rebuildCache();
                    myCurrentFileTodos.rebuildCache();
                  }
                }
              );
              ApplicationManager.getApplication().invokeLater(new Runnable(){
                public void run(){
                  myAllTodos.updateTree();
                  myCurrentFileTodos.updateTree();
                }
              }, ModalityState.NON_MMODAL);
            }
          }, IdeBundle.message("progress.looking.for.todos"), false, myProject);
        }
      });
    }
  }
}