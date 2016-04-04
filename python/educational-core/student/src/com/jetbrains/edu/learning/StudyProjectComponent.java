package com.jetbrains.edu.learning;

import com.intellij.ide.ui.UISettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.editor.StudyEditorFactoryListener;
import com.jetbrains.edu.learning.ui.StudyProgressToolWindowFactory;
import com.jetbrains.edu.learning.ui.StudyToolWindowFactory;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class StudyProjectComponent implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(StudyProjectComponent.class.getName());
  private final Project myProject;
  private FileCreatedByUserListener myListener;
  // Shows could we use JavaFX Task Description panel or should use Swing
  private boolean useJavaFx = true;
  private Map<Keymap, List<Pair<String, String>>> myDeletedShortcuts = new HashMap<Keymap, List<Pair<String, String>>>();
  private StudyProjectComponent(@NotNull final Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    // Check if user has javafx lib in his JDK. Now bundled JDK doesn't have this lib inside.
    try {
      Platform.setImplicitExit(false);
    }
    catch (NoClassDefFoundError e) {
      useJavaFx = false;
    }

    if (course != null && !course.isUpToDate()) {
      course.setUpToDate(true);
      updateCourse();
    }

    registerStudyToolWindow(course);
    ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new DumbAwareRunnable() {
          @Override
          public void run() {
            if (course != null) {
              UISettings.getInstance().HIDE_TOOL_STRIPES = false;
              UISettings.getInstance().fireUISettingsChanged();
              registerShortcuts();
            }
          }
        });
      }
    });
  }

  public void registerStudyToolWindow(@Nullable final Course course) {
    if (course != null && "PyCharm".equals(course.getCourseType())) {
      final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      registerToolWindows(toolWindowManager);
      final ToolWindow studyToolWindow = toolWindowManager.getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW);
      final ToolWindow progressToolWindow = toolWindowManager.getToolWindow(StudyProgressToolWindowFactory.ID);
      if (studyToolWindow != null) {
        studyToolWindow.show(null);
      }
      if (progressToolWindow != null) {
        StudyUtils.updateToolWindows(myProject);
        progressToolWindow.show(null);
      }
    }
  }

  private void registerShortcuts() {
    addShortcut(StudyNextWindowAction.ACTION_ID, new String[]{StudyNextWindowAction.SHORTCUT, StudyNextWindowAction.SHORTCUT2});
    addShortcut(StudyPrevWindowAction.ACTION_ID, new String[]{StudyPrevWindowAction.SHORTCUT});
    addShortcut(StudyShowHintAction.ACTION_ID, new String[]{StudyShowHintAction.SHORTCUT});
    addShortcut(StudyCheckAction.ACTION_ID, new String[]{StudyCheckAction.SHORTCUT});
    addShortcut(StudyNextStudyTaskAction.ACTION_ID, new String[]{StudyNextStudyTaskAction.SHORTCUT});
    addShortcut(StudyPreviousStudyTaskAction.ACTION_ID, new String[]{StudyPreviousStudyTaskAction.SHORTCUT});
    addShortcut(StudyRefreshTaskFileAction.ACTION_ID, new String[]{StudyRefreshTaskFileAction.SHORTCUT});
  }

  private void registerToolWindows(@NotNull final ToolWindowManager toolWindowManager) {
    final ToolWindow toolWindow = toolWindowManager.getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW);
    if (toolWindow == null) {
      toolWindowManager.registerToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW, true, ToolWindowAnchor.RIGHT, myProject, true);
    }
    ToolWindow progressToolWindow = toolWindowManager.getToolWindow(StudyProgressToolWindowFactory.ID);
    if (progressToolWindow == null) {
      toolWindowManager.registerToolWindow(StudyProgressToolWindowFactory.ID, true, ToolWindowAnchor.LEFT, myProject, true, true);
    }
  }

  private void updateCourse() {
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    if (course == null) {
      return;
    }
    final File resourceDirectory = new File(course.getCourseDirectory());
    if (!resourceDirectory.exists()) {
      return;
    }
    StudyLanguageManager manager = StudyUtils.getLanguageManager(course);
    if (manager == null) {
      LOG.info("Study Language Manager is null for " + course.getLanguageById().getDisplayName());
      return;
    }
    final File[] files = resourceDirectory.listFiles();
    if (files == null) return;
    for (File file : files) {
      String testHelper = manager.getTestHelperFileName();
      if (file.getName().equals(testHelper)) {
        copyFile(file, new File(myProject.getBasePath(), testHelper));
      }
      if (file.getName().startsWith(EduNames.LESSON)) {
        final File[] tasks = file.listFiles();
        if (tasks == null) continue;
        for (File task : tasks) {
          final File taskDescr = new File(task, EduNames.TASK_HTML);
          String testFileName = manager.getTestFileName();
          final File taskTests = new File(task, testFileName);
          copyFile(taskDescr, new File(new File(new File(myProject.getBasePath(), file.getName()), task.getName()), EduNames.TASK_HTML));
          copyFile(taskTests, new File(new File(new File(myProject.getBasePath(), file.getName()), task.getName()),
                                       testFileName));
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

  private void addShortcut(@NotNull final String actionIdString, @NotNull final String[] shortcuts) {
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    for (Keymap keymap : keymapManager.getAllKeymaps()) {
      List<Pair<String, String>> pairs = myDeletedShortcuts.get(keymap);
      if (pairs == null) {
        pairs = new ArrayList<Pair<String, String>>();
        myDeletedShortcuts.put(keymap, pairs);
      }
      for (String shortcutString : shortcuts) {
        Shortcut studyActionShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(shortcutString), null);
        String[] actionsIds = keymap.getActionIds(studyActionShortcut);
        for (String actionId : actionsIds) {
          pairs.add(Pair.create(actionId, shortcutString));
          keymap.removeShortcut(actionId, studyActionShortcut);
        }
        keymap.addShortcut(actionIdString, studyActionShortcut);
      }
    }
  }

  @Override
  public void projectClosed() {
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    if (course != null) {
      final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW);
      if (toolWindow != null) {
        toolWindow.getContentManager().removeAllContents(false);
      }
      final ToolWindow progressToolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(StudyProgressToolWindowFactory.ID);
      if (progressToolWindow != null) {
        progressToolWindow.getContentManager().removeAllContents(false);
      }
      KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
      for (Keymap keymap : keymapManager.getAllKeymaps()) {
        List<Pair<String, String>> pairs = myDeletedShortcuts.get(keymap);
        if (pairs != null && !pairs.isEmpty()) {
          for (Pair<String, String> actionShortcut : pairs) {
            keymap.addShortcut(actionShortcut.first, new KeyboardShortcut(KeyStroke.getKeyStroke(actionShortcut.second), null));
          }
        }
       }
    }
    myListener = null;
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

  public static StudyProjectComponent getInstance(@NotNull final Project project) {
    final Module module = ModuleManager.getInstance(project).getModules()[0];
    return module.getComponent(StudyProjectComponent.class);
  }

  public boolean useJavaFx() {
    return useJavaFx;
  }

  private class FileCreatedByUserListener extends VirtualFileAdapter {
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      if (myProject.isDisposed()) return;
      final VirtualFile createdFile = event.getFile();
      final VirtualFile taskDir = createdFile.getParent();
      final Course course = StudyTaskManager.getInstance(myProject).getCourse();
      if (taskDir != null && taskDir.getName().contains(EduNames.TASK)) {
        int taskIndex = EduUtils.getIndex(taskDir.getName(), EduNames.TASK);
        final VirtualFile lessonDir = taskDir.getParent();
        if (lessonDir != null && lessonDir.getName().contains(EduNames.LESSON)) {
          int lessonIndex = EduUtils.getIndex(lessonDir.getName(), EduNames.LESSON);
          if (course != null) {
            List<Lesson> lessons = course.getLessons();
            if (StudyUtils.indexIsValid(lessonIndex, lessons)) {
              final Lesson lesson = lessons.get(lessonIndex);
              final List<Task> tasks = lesson.getTaskList();
              if (StudyUtils.indexIsValid(taskIndex, tasks)) {
                final Task task = tasks.get(taskIndex);
                final TaskFile taskFile = new TaskFile();
                taskFile.initTaskFile(task, false);
                taskFile.setUserCreated(true);
                final String name = createdFile.getName();
                taskFile.name = name;
                task.getTaskFiles().put(name, taskFile);
              }
            }
          }
        }
      }
    }
  }

}
