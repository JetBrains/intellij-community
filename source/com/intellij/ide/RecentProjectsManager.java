package com.intellij.ide;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

public class RecentProjectsManager implements ApplicationComponent, JDOMExternalizable {
  private ArrayList<String> myRecentProjects = new ArrayList<String>();
  private String myLastProjectPath;
  private static final int MAX_RECENT_PROJECTS = 15;
  @NonNls
  private static final String ELEMENT_LAST_PROJECT = "last_project";
  @NonNls
  private static final String ATTRIBUTE_PATH = "path";
  @NonNls
  private static final String ELEMENT_PROJECT = "project";

  public RecentProjectsManager(ProjectManager projectManager) {
    projectManager.addProjectManagerListener(new MyProjectManagerListener());
  }

  public static RecentProjectsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(RecentProjectsManager.class);
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    for (final Object o : element.getChildren()) {
      Element e = (Element)o;
      if (ELEMENT_LAST_PROJECT.equals(e.getName())) {
        myLastProjectPath = e.getAttributeValue(ATTRIBUTE_PATH);
      }

      if (ELEMENT_PROJECT.equals(e.getName())) {
        String path = e.getAttributeValue(ATTRIBUTE_PATH);
        File file = new File(path);
        if (file.exists()) {
          myRecentProjects.add(path);
        }
      }
    }
    if (myLastProjectPath != null) {
      File file = new File(myLastProjectPath);
      if (!file.exists()) {
        myLastProjectPath = null;
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    validateRecentProjects();
    if (myLastProjectPath != null) {
      Element e = new Element(ELEMENT_LAST_PROJECT);
      e.setAttribute(ATTRIBUTE_PATH, myLastProjectPath);
      element.addContent(e);
    }
    for (String path : myRecentProjects) {
      Element e = new Element(ELEMENT_PROJECT);
      e.setAttribute(ATTRIBUTE_PATH, path);
      element.addContent(e);
    }
  }

  private void validateRecentProjects() {
    for (Iterator i = myRecentProjects.iterator(); i.hasNext();) {
      String s = (String)i.next();
      if (s == null || !(new File(s).exists())) {
        i.remove();
      }
    }
    while (myRecentProjects.size() > MAX_RECENT_PROJECTS) {
      myRecentProjects.remove(myRecentProjects.size() - 1);
    }
  }

  public String getLastProjectPath() {
    return myLastProjectPath;
  }

  public void updateLastProjectPath() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) {
      myLastProjectPath = null;
    } else {
      myLastProjectPath = getProjectPath(openProjects[openProjects.length - 1]);
    }
  }

  public String getComponentName() {
    return "RecentProjectsManager";
  }

  private void removePath(String path) {
    if (SystemInfo.isFileSystemCaseSensitive) {
      myRecentProjects.remove(path);
    }
    else {
      Iterator<String> i = myRecentProjects.iterator();
      while (i.hasNext()) {
        String p = i.next();
        if (path.equalsIgnoreCase(p)) {
          i.remove();
        }
      }
    }
  }


  /**
   * @param addClearListItem - used for detecting whether the "Clear List" action should be added
   * to the end of the returned list of actions
   * @return
   */
  public AnAction[] getRecentProjectsActions(boolean addClearListItem) {
    validateRecentProjects();

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

    ArrayList<AnAction> actions = new ArrayList<AnAction>();
    outer: for (String myRecentProject : myRecentProjects) {
      String projectPath = myRecentProject.toString();

      for (Project openProject : openProjects) {
        if (projectPath.equals(getProjectPath(openProject))) {
          continue outer;
        }
      }

      actions.add(new ReopenProjectAction(projectPath));
    }


    if (actions.size() == 0) {
      return new AnAction[0];
    }

    ArrayList<AnAction> list = new ArrayList<AnAction>();
    for (AnAction action : actions) {
      list.add(action);
    }
    if (addClearListItem) {
      AnAction clearListAction = new AnAction(IdeBundle.message("action.clear.list")) {
        public void actionPerformed(AnActionEvent e) {
          myRecentProjects.clear();
        }
      };
      list.add(Separator.getInstance());
      list.add(clearListAction);
    }
    
    return list.toArray(new AnAction[list.size()]);
  }

  public String[] getRecentProjectPaths() {
    validateRecentProjects();
    return myRecentProjects.toArray(new String[myRecentProjects.size()]);
  }

  private class MyProjectManagerListener implements ProjectManagerListener{

    public void projectOpened(Project project) {
      String path = getProjectPath(project);
      myLastProjectPath = path;
      removePath(path);
      myRecentProjects.add(0, path);
    }

    public boolean canCloseProject(Project project) {
      return true;
    }

    public void projectClosing(Project project) {
    }

    public void projectClosed(Project project) {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      if (openProjects.length > 0) {
        String path = getProjectPath(openProjects[openProjects.length - 1]);
        myLastProjectPath = path;
        removePath(path);
        myRecentProjects.add(0, path);
      }
    }
  }

  private static String getProjectPath(Project project) {
    return project.getLocation().replace('/', File.separatorChar);
  }

  private static class ReopenProjectAction extends AnAction {
    private final String myProjectPath;

    public ReopenProjectAction(String projectPath) {
      myProjectPath = projectPath;
      getTemplatePresentation().setText(projectPath, false);
    }

    public void actionPerformed(AnActionEvent e) {
      ProjectUtil.openProject(myProjectPath, (Project)e.getDataContext().getData(DataConstants.PROJECT), false);
    }
  }
}
