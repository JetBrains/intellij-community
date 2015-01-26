package com.jetbrains.edu.learning;

import com.intellij.ide.ui.UISettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.*;
import com.intellij.util.xmlb.XmlSerializer;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.course.Course;
import com.jetbrains.edu.learning.course.Lesson;
import com.jetbrains.edu.learning.course.Task;
import com.jetbrains.edu.learning.course.TaskFile;
import com.jetbrains.edu.learning.ui.StudyCondition;
import com.jetbrains.edu.learning.ui.StudyToolWindowFactory;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
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
  private static Map<String, String> myDeletedShortcuts = new HashMap<String, String>();
  private final Project myProject;
  private Course myCourse;
  private FileCreatedByUserListener myListener;


  public void setCourse(@NotNull final Course course) {
    myCourse = course;
  }

  private StudyTaskManager(@NotNull final Project project) {
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
    if (myCourse != null && !myCourse.isUpToDate()) {
      myCourse.setUpToDate(true);
      updateCourse();
    }
    ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new DumbAwareRunnable() {
          @Override
          public void run() {
            if (myCourse != null) {
              moveFocusToEditor();
              UISettings.getInstance().HIDE_TOOL_STRIPES = false;
              UISettings.getInstance().fireUISettingsChanged();
              final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
              registerToolWindow(toolWindowManager);
              final ToolWindow studyToolWindow = toolWindowManager.getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW);
              if (studyToolWindow != null) {
                StudyUtils.updateStudyToolWindow(myProject);
                studyToolWindow.show(null);
              }
              registerShortcuts();
            }
          }
        });
      }
    });
  }

  private void moveFocusToEditor() {
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
  }

  private static void registerShortcuts() {
    addShortcut(StudyNextWindowAction.SHORTCUT, StudyNextWindowAction.ACTION_ID, false);
    addShortcut(StudyPrevWindowAction.SHORTCUT, StudyPrevWindowAction.ACTION_ID, false);
    addShortcut(StudyShowHintAction.SHORTCUT, StudyShowHintAction.ACTION_ID, false);
    addShortcut(StudyNextWindowAction.SHORTCUT2, StudyNextWindowAction.ACTION_ID, true);
    addShortcut(StudyCheckAction.SHORTCUT, StudyCheckAction.ACTION_ID, false);
    addShortcut(StudyNextStudyTaskAction.SHORTCUT, StudyNextStudyTaskAction.ACTION_ID, false);
    addShortcut(StudyPreviousStudyTaskAction.SHORTCUT, StudyPreviousStudyTaskAction.ACTION_ID, false);
    addShortcut(StudyRefreshTaskFileAction.SHORTCUT, StudyRefreshTaskFileAction.ACTION_ID, false);
  }

  private void registerToolWindow(@NotNull final ToolWindowManager toolWindowManager) {
    try {
      Method method = toolWindowManager.getClass().getDeclaredMethod("registerToolWindow", String.class,
                                                                     JComponent.class,
                                                                     ToolWindowAnchor.class,
                                                                     boolean.class, boolean.class, boolean.class);
      method.setAccessible(true);
      method.invoke(toolWindowManager, StudyToolWindowFactory.STUDY_TOOL_WINDOW, null, ToolWindowAnchor.LEFT, true, true, true);
    }
    catch (Exception e) {
      final ToolWindow toolWindow = toolWindowManager.getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW);
      if (toolWindow == null) {
        toolWindowManager.registerToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW, true, ToolWindowAnchor.RIGHT, myProject, true);
      }
    }
  }

  private void updateCourse() {
    if (myCourse == null) {
      return;
    }
    final File resourceDirectory = new File(myCourse.getCourseDirectory());
    if (!resourceDirectory.exists()) {
      return;
    }
    final File[] files = resourceDirectory.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file.getName().equals(StudyNames.TEST_HELPER)) {
        copyFile(file, new File(myProject.getBasePath(), StudyNames.TEST_HELPER));
      }
      if (file.getName().startsWith(StudyNames.LESSON)) {
        final File[] tasks = file.listFiles();
        if (tasks == null) continue;
        for (File task : tasks) {
          final File taskDescr = new File(task, StudyNames.TASK_HTML);
          final File taskTests = new File(task, StudyNames.TASK_TESTS);
          copyFile(taskDescr, new File(new File(new File(myProject.getBasePath(), file.getName()), task.getName()), StudyNames.TASK_HTML));
          copyFile(taskTests, new File(new File(new File(myProject.getBasePath(), file.getName()), task.getName()), StudyNames.TASK_TESTS));
        }
      }
    }

    final Notification notification =
      new Notification("Update.course", "Course update", "Current course is synchronized", NotificationType.INFORMATION);
    notification.notify(myProject);
  }

  private static void copyFile(@NotNull final File from, @NotNull final File to) {
    if (from.exists()) {
      try {
        FileUtil.copy(from, to);
      }
      catch (IOException e) {
        LOG.warn("Failed to copy " + from.getName());
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
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    StudyCondition.VALUE = false;
    if (myCourse != null) {
      ToolWindowManager.getInstance(myProject).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW).getContentManager()
        .removeAllContents(false);
      if (!myDeletedShortcuts.isEmpty()) {
        for (Map.Entry<String, String> shortcut : myDeletedShortcuts.entrySet()) {
          final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
          final Shortcut actionShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(shortcut.getValue()), null);
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
            myListener =  new FileCreatedByUserListener();
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
    final Module module = ModuleManager.getInstance(project).getModules()[0];
    return ModuleServiceManager.getService(module, StudyTaskManager.class);
  }

  @Nullable
  public TaskFile getTaskFile(@NotNull final VirtualFile file) {
    if (myCourse == null) {
      return null;
    }
    final VirtualFile taskDir = file.getParent();
    if (taskDir == null) {
      return null;
    }
    final String taskDirName = taskDir.getName();
    if (taskDirName.contains(Task.TASK_DIR)) {
      final VirtualFile lessonDir = taskDir.getParent();
      if (lessonDir != null) {
        int lessonIndex = StudyUtils.getIndex(lessonDir.getName(), StudyNames.LESSON_DIR);
        List<Lesson> lessons = myCourse.getLessons();
        if (!StudyUtils.indexIsValid(lessonIndex, lessons)) {
          return null;
        }
        final Lesson lesson = lessons.get(lessonIndex);
        int taskIndex = StudyUtils.getIndex(taskDirName, Task.TASK_DIR);
        final List<Task> tasks = lesson.getTaskList();
        if (!StudyUtils.indexIsValid(taskIndex, tasks)) {
          return null;
        }
        final Task task = tasks.get(taskIndex);
        return task.getFile(file.getName());
      }
    }
    return null;
  }

  private class FileCreatedByUserListener extends VirtualFileAdapter {
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      final VirtualFile createdFile = event.getFile();
      final VirtualFile taskDir = createdFile.getParent();
      if (taskDir != null && taskDir.getName().contains(Task.TASK_DIR)) {
        int taskIndex = StudyUtils.getIndex(taskDir.getName(), Task.TASK_DIR);
        final VirtualFile lessonDir = taskDir.getParent();
        if (lessonDir != null && lessonDir.getName().contains(StudyNames.LESSON_DIR)) {
          int lessonIndex = StudyUtils.getIndex(lessonDir.getName(), StudyNames.LESSON_DIR);
          if (myCourse != null) {
            List<Lesson> lessons = myCourse.getLessons();
            if (StudyUtils.indexIsValid(lessonIndex, lessons)) {
              final Lesson lesson = lessons.get(lessonIndex);
              final List<Task> tasks = lesson.getTaskList();
              if (StudyUtils.indexIsValid(taskIndex, tasks)) {
                final Task task = tasks.get(taskIndex);
                final TaskFile taskFile = new TaskFile();
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
