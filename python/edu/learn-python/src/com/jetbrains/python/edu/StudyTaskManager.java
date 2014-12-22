package com.jetbrains.python.edu;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.*;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.xmlb.XmlSerializer;
import com.jetbrains.python.edu.actions.*;
import com.jetbrains.python.edu.course.Course;
import com.jetbrains.python.edu.course.Lesson;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.ui.StudyCondition;
import com.jetbrains.python.edu.ui.StudyToolWindowFactory;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of class which contains all the information
 * about study in context of current project
 */

@State(
  name = "StudySettings",
  storages = {
    @Storage(
      id = "others",
      file = "$PROJECT_CONFIG_DIR$/study_project.xml",
      scheme = StorageScheme.DIRECTORY_BASED
    )}
)
public class StudyTaskManager implements ProjectComponent, PersistentStateComponent<Element>, DumbAware {
  private static final Logger LOG = Logger.getInstance(StudyTaskManager.class.getName());
  public static final String COURSE_ELEMENT = "courseElement";
  private static Map<String, StudyTaskManager> myTaskManagers = new HashMap<String, StudyTaskManager>();
  private static Map<String, String> myDeletedShortcuts = new HashMap<String, String>();
  private final Project myProject;
  private Course myCourse;
  private FileCreatedListener myListener;


  public void setCourse(Course course) {
    myCourse = course;
  }

  private StudyTaskManager(@NotNull final Project project) {
    myTaskManagers.put(project.getBasePath(), this);
    myProject = project;
  }


  @Nullable
  public Course getCourse() {
    return myCourse;
  }

  @Nullable
  @Override
  public Element getState() {
    Element el = new Element("taskManager");
    if (myCourse != null) {
      Element courseElement = new Element(COURSE_ELEMENT);
      XmlSerializer.serializeInto(myCourse, courseElement);
      el.addContent(courseElement);
    }
    return el;
  }

  @Override
  public void loadState(Element el) {
    myCourse = XmlSerializer.deserialize(el.getChild(COURSE_ELEMENT), Course.class);
    if (myCourse != null) {
      myCourse.init(true);
    }
  }

  @Override
  public void projectOpened() {
    final File pythonIntroduction = new File(ProjectUtil.getBaseDir(), "PythonIntroduction");
    if (StudyInitialConfigurator.UPDATE_PROJECT && myProject.getBasePath().equals(pythonIntroduction.getAbsolutePath())) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      StudyInitialConfigurator.UPDATE_PROJECT = false;
      updateCourse();
    }
    ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new DumbAwareRunnable() {
          @Override
          public void run() {
            if (myCourse != null) {
              StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
                @Override
                public void run() {
                  ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.PROJECT_VIEW).show(new Runnable() {
                    @Override
                    public void run() {
                      FileEditor[] editors = FileEditorManager.getInstance(myProject).getSelectedEditors();
                      if (editors.length > 0) {
                        final JComponent focusedComponent = editors[0].getPreferredFocusedComponent();
                        if (focusedComponent != null) {
                          ApplicationManager.getApplication().invokeLater(new Runnable() {
                            @Override
                            public void run() {
                              IdeFocusManager.getInstance(myProject).requestFocus(focusedComponent, true);
                            }
                          });
                        }
                      }
                    }
                  });
                }
              });
              UISettings.getInstance().HIDE_TOOL_STRIPES = false;
              UISettings.getInstance().fireUISettingsChanged();
              ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
              String toolWindowId = StudyToolWindowFactory.STUDY_TOOL_WINDOW;
              try {
                Method method = toolWindowManager.getClass().getDeclaredMethod("registerToolWindow", String.class,
                                                                               JComponent.class,
                                                                               ToolWindowAnchor.class,
                                                                               boolean.class, boolean.class, boolean.class);
                method.setAccessible(true);
                method.invoke(toolWindowManager, toolWindowId, null, ToolWindowAnchor.LEFT, true, true, true);
              }
              catch (Exception e) {
                final ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
                if (toolWindow == null) {
                  toolWindowManager.registerToolWindow(toolWindowId, true, ToolWindowAnchor.RIGHT, myProject, true);
                }
              }

              final ToolWindow studyToolWindow = toolWindowManager.getToolWindow(toolWindowId);
              class UrlOpeningListener implements NotificationListener {
                private final boolean myExpireNotification;

                public UrlOpeningListener(boolean expireNotification) {
                  myExpireNotification = expireNotification;
                }

                protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                  URL url = event.getURL();
                  if (url == null) {
                    BrowserUtil.browse(event.getDescription());
                  }
                  else {
                    BrowserUtil.browse(url);
                  }
                  if (myExpireNotification) {
                    notification.expire();
                  }
                }

                @Override
                public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                  if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    hyperlinkActivated(notification, event);
                  }
                }
              }
              if (studyToolWindow != null) {
                StudyUtils.updateStudyToolWindow(myProject);
                studyToolWindow.show(null);
                UiNotifyConnector.doWhenFirstShown(studyToolWindow.getComponent(), new Runnable() {
                  @Override
                  public void run() {
                    if (PropertiesComponent.getInstance().getBoolean("StudyShowPopup", true)) {
                      String content = "<html>If you'd like to learn" +
                                       " more about PyCharm " +
                                       "Educational Edition, " +
                                       "click <a href=\"https://www.jetbrains.com/pycharm-educational/quickstart/\">here</a> to watch a tutorial</html>";
                      final Notification notification =
                        new Notification("Watch Tutorials!", "", content, NotificationType.INFORMATION, new UrlOpeningListener(true));
                      Notifications.Bus.notify(notification);
                      Balloon balloon = notification.getBalloon();
                      if (balloon != null) {
                        balloon.addListener(new JBPopupAdapter() {
                          @Override
                          public void onClosed(LightweightWindowEvent event) {
                            notification.expire();
                          }
                        });
                      }
                      notification.whenExpired(new Runnable() {
                        @Override
                        public void run() {
                          PropertiesComponent.getInstance().setValue("StudyShowPopup", String.valueOf(false));
                        }
                      });
                    }
                  }
                });
              }
              addShortcut(StudyNextWindowAction.SHORTCUT, StudyNextWindowAction.ACTION_ID, false);
              addShortcut(StudyPrevWindowAction.SHORTCUT, StudyPrevWindowAction.ACTION_ID, false);
              addShortcut(StudyShowHintAction.SHORTCUT, StudyShowHintAction.ACTION_ID, false);
              addShortcut(StudyNextWindowAction.SHORTCUT2, StudyNextWindowAction.ACTION_ID, true);
              addShortcut(StudyCheckAction.SHORTCUT, StudyCheckAction.ACTION_ID, false);
              addShortcut(StudyNextStudyTaskAction.SHORTCUT, StudyNextStudyTaskAction.ACTION_ID, false);
              addShortcut(StudyPreviousStudyTaskAction.SHORTCUT, StudyPreviousStudyTaskAction.ACTION_ID, false);
              addShortcut(StudyRefreshTaskFileAction.SHORTCUT, StudyRefreshTaskFileAction.ACTION_ID, false);
            }
          }
        });
      }
    });
  }

  private void updateCourse() {
    final File userCourseDir = new File(PathManager.getConfigPath(), StudyNames.COURSES);
    final File courseDir = new File(userCourseDir, StudyNames.INTRODUCTION_COURSE);
    final File[] files = courseDir.listFiles();
    if (files == null) return;
    for (File lesson : files) {
      if (lesson.getName().startsWith(StudyNames.LESSON)) {
        final File[] tasks = lesson.listFiles();
        if (tasks == null) continue;
        for (File task : tasks) {
          final File taskDescr = new File(task, StudyNames.TASK_HTML);
          final File taskTests = new File(task, StudyNames.TASK_TESTS);
          copyFile(lesson, task, taskDescr, StudyNames.TASK_HTML);
          copyFile(lesson, task, taskTests, StudyNames.TASK_TESTS);
        }
      }
    }

    final Notification notification =
      new Notification("Update.course", "Course update", "Current course is synchronized", NotificationType.INFORMATION);
    notification.notify(myProject);
  }

  private void copyFile(@NotNull final File lesson, @NotNull final File task, @NotNull final File taskDescr,
                        @NotNull final String fileName) {
    if (taskDescr.exists()) {
      try {
        FileUtil.copy(taskDescr, new File(new File(new File(myProject.getBasePath(), lesson.getName()), task.getName()), fileName));
      }
      catch (IOException e) {
        LOG.warn("Failed to copy " + lesson.getName() + " " + task.getName());
      }
    }
  }

  private static void addShortcut(@NotNull final String shortcutString, @NotNull final String actionIdString, boolean isAdditional) {
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = keymap.getShortcuts(actionIdString);
    if (shortcuts.length > 0 && !isAdditional) {
      return;
    }
    Shortcut studyActionShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(shortcutString), null);
    String[] actionsIds = keymap.getActionIds(studyActionShortcut);
    for (String actionId : actionsIds) {
      myDeletedShortcuts.put(actionId, shortcutString);
      keymap.removeShortcut(actionId, studyActionShortcut);
    }
    keymap.addShortcut(actionIdString, studyActionShortcut);
  }

  @Override
  public void projectClosed() {
    StudyCondition.VALUE = false;
    if (myCourse != null) {
      ToolWindowManager.getInstance(myProject).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW).getContentManager()
        .removeAllContents(false);
      if (!myDeletedShortcuts.isEmpty()) {
        for (Map.Entry<String, String> shortcut : myDeletedShortcuts.entrySet()) {
          Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
          Shortcut actionShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(shortcut.getValue()), null);
          keymap.addShortcut(shortcut.getKey(), actionShortcut);
        }
      }
    }
  }

  @Override
  public void initComponent() {
    EditorFactory.getInstance().addEditorFactoryListener(new StudyEditorFactoryListener(), myProject);
    ActionManager.getInstance().addAnActionListener(new AnActionListener() {
      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        AnAction[] newGroupActions = ((ActionGroup)ActionManager.getInstance().getAction("NewGroup")).getChildren(null);
        for (AnAction newAction : newGroupActions) {
          if (newAction == action) {
            myListener =  new FileCreatedListener();
            VirtualFileManager.getInstance().addVirtualFileListener(myListener);
            break;
          }
        }
      }

      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        AnAction[] newGroupActions = ((ActionGroup)ActionManager.getInstance().getAction("NewGroup")).getChildren(null);
        for (AnAction newAction : newGroupActions) {
          if (newAction == action) {
            VirtualFileManager.getInstance().removeVirtualFileListener(myListener);
          }
        }
      }

      @Override
      public void beforeEditorTyping(char c, DataContext dataContext) {

      }
    });
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "StudyTaskManager";
  }

  public static StudyTaskManager getInstance(@NotNull final Project project) {
    StudyTaskManager item = myTaskManagers.get(project.getBasePath());
    return item != null ? item : new StudyTaskManager(project);
  }


  @Nullable
  public TaskFile getTaskFile(@NotNull final VirtualFile file) {
    if (myCourse == null) {
      return null;
    }
    VirtualFile taskDir = file.getParent();
    if (taskDir != null) {
      String taskDirName = taskDir.getName();
      if (taskDirName.contains(Task.TASK_DIR)) {
        VirtualFile lessonDir = taskDir.getParent();
        if (lessonDir != null) {
          String lessonDirName = lessonDir.getName();
          int lessonIndex = StudyUtils.getIndex(lessonDirName, Lesson.LESSON_DIR);
          List<Lesson> lessons = myCourse.getLessons();
          if (!StudyUtils.indexIsValid(lessonIndex, lessons)) {
            return null;
          }
          Lesson lesson = lessons.get(lessonIndex);
          int taskIndex = StudyUtils.getIndex(taskDirName, Task.TASK_DIR);
          List<Task> tasks = lesson.getTaskList();
          if (!StudyUtils.indexIsValid(taskIndex, tasks)) {
            return null;
          }
          Task task = tasks.get(taskIndex);
          return task.getFile(file.getName());
        }
      }
    }
    return null;
  }

  class FileCreatedListener extends VirtualFileAdapter {
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      VirtualFile createdFile = event.getFile();
      VirtualFile taskDir = createdFile.getParent();
      String taskLogicalName = Task.TASK_DIR;
      if (taskDir != null && taskDir.getName().contains(taskLogicalName)) {
        int taskIndex = StudyUtils.getIndex(taskDir.getName(), taskLogicalName);
        VirtualFile lessonDir = taskDir.getParent();
        String lessonLogicalName = Lesson.LESSON_DIR;
        if (lessonDir != null && lessonDir.getName().contains(lessonLogicalName)) {
          int lessonIndex = StudyUtils.getIndex(lessonDir.getName(), lessonLogicalName);
          if (myCourse != null) {
            List<Lesson> lessons = myCourse.getLessons();
            if (StudyUtils.indexIsValid(lessonIndex, lessons)) {
              Lesson lesson = lessons.get(lessonIndex);
              List<Task> tasks = lesson.getTaskList();
              if (StudyUtils.indexIsValid(taskIndex, tasks)) {
                Task task = tasks.get(taskIndex);
                TaskFile taskFile = new TaskFile();
                taskFile.init(task, false);
                taskFile.setUserCreated(true);
                task.getTaskFiles().put(createdFile.getName(), taskFile);
              }
            }
          }
        }
      }
    }
  }

}
