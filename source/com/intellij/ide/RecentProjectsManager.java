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

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

public class RecentProjectsManager implements ApplicationComponent, JDOMExternalizable {
  private ArrayList<String> myRecentProjects = new ArrayList<String>();
  private String myLastProjectPath;
  private static final int MAX_RECENT_PROJECTS = 15;

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
    for (Iterator i=element.getChildren().iterator();i.hasNext();) {
      Element e=(Element)i.next();
      if ("last_project".equals(e.getName())) {
        myLastProjectPath = e.getAttributeValue("path");
      }

      if ("project".equals(e.getName())) {
        String path = e.getAttributeValue("path");
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
      Element e = new Element("last_project");
      e.setAttribute("path", myLastProjectPath);
      element.addContent(e);
    }
    for (int i = 0; i < myRecentProjects.size(); i++) {
      String path = myRecentProjects.get(i);
      Element e = new Element("project");
      e.setAttribute("path", path);
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

  public AnAction[] getRecentProjectsActions() {
    validateRecentProjects();

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

    ArrayList<AnAction> actions = new ArrayList<AnAction>();
    outer: for (int i = 0; i < myRecentProjects.size(); i++) {
      String projectPath = myRecentProjects.get(i).toString();

      for (int j = 0; j < openProjects.length; j++) {
        Project openProject = openProjects[j];
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
    for (int i = 0; i < actions.size(); i++) {
      AnAction action = actions.get(i);
      list.add(action);
    }
    AnAction clearListAction = new AnAction("Clear List") {
      public void actionPerformed(AnActionEvent e) {
        myRecentProjects.clear();
      }
    };
    list.add(Separator.getInstance());
    list.add(clearListAction);
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
    return project.getProjectFilePath();
  }

  private class ReopenProjectAction extends AnAction {
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
