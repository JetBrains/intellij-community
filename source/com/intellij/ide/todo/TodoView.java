package com.intellij.ide.todo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
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

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;

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

  /* Invoked by reflection */
  TodoView(Project project){
    myProject=project;
    myCurrentPanelSettings=new TodoPanelSettings();
    myAllPanelSettings=new TodoPanelSettings();
  }

  public void readExternal(Element element) throws InvalidDataException{
    mySelectedIndex=0;
    try{
      mySelectedIndex=Integer.parseInt(element.getAttributeValue("selected-index"));
    }catch(NumberFormatException ignored){}

    for(Iterator i=element.getChildren().iterator();i.hasNext();){
      Element child=(Element)i.next();
      if("todo-panel".equals(child.getName())){
        String id=child.getAttributeValue("id");
        if("selected-file".equals(id)){
          myCurrentPanelSettings.readExternal(child);
        }else if("all".equals(id)){
          myAllPanelSettings.readExternal(child);
        }else{
          throw new IllegalArgumentException("unknown id: "+id);
        }
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException{
    if(myContentManager!=null){ // all panel were constructed
      Content content=myContentManager.getSelectedContent();
      element.setAttribute("selected-index",Integer.toString(myContentManager.getIndexOfContent(content)));
    }

    Element selectedFileElement=new Element("todo-panel");
    selectedFileElement.setAttribute("id","selected-file");
    myCurrentPanelSettings.writeExternal(selectedFileElement);
    element.addContent(selectedFileElement);

    Element allElement=new Element("todo-panel");
    allElement.setAttribute("id","all");
    myAllPanelSettings.writeExternal(allElement);
    element.addContent(allElement);
  }

  public void disposeComponent(){}

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

          Content allTodosContent=PeerFactory.getInstance().getContentFactory().createContent(null,"Project",false);
          myAllTodos=new TodoPanel(myProject,myAllPanelSettings,false,allTodosContent){
            protected TodoTreeBuilder createTreeBuilder(JTree tree,DefaultTreeModel treeModel,Project project){
              AllTodosTreeBuilder builder=new AllTodosTreeBuilder(tree,treeModel,project);
              builder.init();
              return builder;
            }
          };
          allTodosContent.setComponent(myAllTodos);

          Content currentFileTodosContent=PeerFactory.getInstance().getContentFactory().createContent(null,"Current File",false);
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
            final ProgressWindow progressWindow = new ProgressWindow(false, null);
            progressWindow.setTitle("Looking for TODOs...");
            progressWindow.setText("Please wait...");
            final Runnable process = new Runnable() {
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
            };
            Thread thread = new Thread(new Runnable() {
              public void run() {
                ProgressManager.getInstance().runProcess(process, progressWindow);
              }
            }, "Todo finder");
            thread.start();
          }
        });
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
      ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run(){
              // [vova] It's very important to pass null as project. Each TODO view shows own progress
              // window. It causes frame switching.
              final ProgressWindow progressWindow=new ProgressWindow(false,null);
              progressWindow.setTitle("Looking for TODOs...");
              progressWindow.setText("Please wait...");
              final Runnable process=new Runnable(){
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
              };
              Thread thread=new Thread(
                new Runnable(){
                  public void run(){
                    ProgressManager.getInstance().runProcess(process,progressWindow);
                  }
                }, "Todo finder"
              );
              thread.start();
            }
          });
    }
  }
}