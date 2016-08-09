package com.jetbrains.edu.learning;

import com.intellij.ide.ui.UISettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.edu.learning.actions.StudyActionWithShortcut;
import com.jetbrains.edu.learning.actions.StudyNextWindowAction;
import com.jetbrains.edu.learning.actions.StudyPrevWindowAction;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.editor.StudyEditorFactoryListener;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.stepic.CourseInfo;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import com.jetbrains.edu.learning.ui.StudyToolWindowFactory;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.jetbrains.edu.learning.StudyUtils.execCancelable;
import static com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator.flushCourse;


public class StudyProjectComponent implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(StudyProjectComponent.class.getName());
  private final Project myProject;
  private FileCreatedByUserListener myListener;
  private Map<Keymap, List<Pair<String, String>>> myDeletedShortcuts = new HashMap<>();
  private StudyProjectComponent(@NotNull final Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
    Course course = StudyTaskManager.getInstance(myProject).getCourse();
    // Check if user has javafx lib in his JDK. Now bundled JDK doesn't have this lib inside.
    if (StudyUtils.hasJavaFx()) {
      Platform.setImplicitExit(false);
    }

    if (course != null && !course.isAdaptive() && !course.isUpToDate()) {
      final Notification notification =
        new Notification("Update.course", "Course Updates", "Course is ready to <a href=\"update\">update</a>", NotificationType.INFORMATION,
                         new NotificationListener() {
                           @Override
                           public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                             FileEditorManagerEx.getInstanceEx(myProject).closeAllFiles();

                             ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                               ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
                               return execCancelable(() -> {
                                 updateCourse();
                                 return true;
                               });
                             }, "Updating Course", true, myProject);
                             EduUtils.synchronize();
                             course.setUpdated();

                           }
                         });
      notification.notify(myProject);

    }

    StudyUtils.registerStudyToolWindow(course, myProject);
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new DumbAwareRunnable() {
          @Override
          public void run() {
            Course course = StudyTaskManager.getInstance(myProject).getCourse();
            if (course != null) {
              final UISettings instance = UISettings.getInstance();
              if (instance != null) {
                instance.HIDE_TOOL_STRIPES = false;
                instance.fireUISettingsChanged();
              }
              registerShortcuts();
              EduUsagesCollector.projectTypeOpened(course.isAdaptive() ? EduNames.ADAPTIVE : EduNames.STUDY);
            }
          }
        });
      }
    }));
  }

  private void registerShortcuts() {
    StudyToolWindow window = StudyUtils.getStudyToolWindow(myProject);
    if (window != null) {
      List<AnAction> actionsOnToolbar = window.getActions(true);
      if (actionsOnToolbar != null) {
        for (AnAction action : actionsOnToolbar) {
          if (action instanceof StudyActionWithShortcut) {
            String id = ((StudyActionWithShortcut)action).getActionId();
            String[] shortcuts = ((StudyActionWithShortcut)action).getShortcuts();
            if (shortcuts != null) {
              addShortcut(id, shortcuts);
            }
          }
        }
        addShortcut(StudyNextWindowAction.ACTION_ID, new String[]{StudyNextWindowAction.SHORTCUT, StudyNextWindowAction.SHORTCUT2});
        addShortcut(StudyPrevWindowAction.ACTION_ID, new String[]{StudyPrevWindowAction.SHORTCUT});
      }
      else {
        LOG.warn("Actions on toolbar are nulls");
      }
    }
  }

  private void updateCourse() {
    final Course currentCourse = StudyTaskManager.getInstance(myProject).getCourse();
    final CourseInfo info = CourseInfo.fromCourse(currentCourse);
    if (info == null) return;

    final File resourceDirectory = new File(currentCourse.getCourseDirectory());
    if (resourceDirectory.exists()) {
      FileUtil.delete(resourceDirectory);
    }

    final Course course = EduStepicConnector.getCourse(myProject, info);

    if (course == null) return;
    flushCourse(myProject, course);
    course.initCourse(false);

    StudyLanguageManager manager = StudyUtils.getLanguageManager(course);
    if (manager == null) {
      LOG.info("Study Language Manager is null for " + course.getLanguageById().getDisplayName());
      return;
    }

    final ArrayList<Lesson> updatedLessons = new ArrayList<>();

    int lessonIndex = 0;
    for (Lesson lesson : course.getLessons()) {
      lessonIndex += 1;
      Lesson studentLesson = currentCourse.getLesson(lesson.getId());
      final String lessonDirName = EduNames.LESSON + String.valueOf(lessonIndex);

      final File lessonDir = new File(myProject.getBasePath(), lessonDirName);
      if (!lessonDir.exists()){
        final File fromLesson = new File(resourceDirectory, lessonDirName);
        try {
          FileUtil.copyDir(fromLesson, lessonDir);
        }
        catch (IOException e) {
          LOG.warn("Failed to copy lesson " + fromLesson.getPath());
        }
        lesson.setIndex(lessonIndex);
        lesson.initLesson(currentCourse, false);
        for (int i = 1; i <= lesson.getTaskList().size(); i++) {
          Task task = lesson.getTaskList().get(i - 1);
          task.setIndex(i);
        }
        updatedLessons.add(lesson);
        continue;
      }
      studentLesson.setIndex(lessonIndex);
      updatedLessons.add(studentLesson);

      int index = 0;
      final ArrayList<Task> tasks = new ArrayList<>();
      for (Task task : lesson.getTaskList()) {
        index += 1;
        final Task studentTask = studentLesson.getTask(task.getStepicId());
        if (studentTask != null && StudyStatus.Solved.equals(studentTask.getStatus())) {
          studentTask.setIndex(index);
          tasks.add(studentTask);
          continue;
        }
        task.initTask(studentLesson, false);
        task.setIndex(index);

        final String taskDirName = EduNames.TASK + String.valueOf(index);
        final File toTask = new File(lessonDir, taskDirName);

        final String taskPath = FileUtil.join(resourceDirectory.getPath(), lessonDirName, taskDirName);
        final File taskDir = new File(taskPath);
        if (!taskDir.exists()) return;
        final File[] taskFiles = taskDir.listFiles();
        if (taskFiles == null) continue;
        for (File fromFile : taskFiles) {
          copyFile(fromFile, new File(toTask, fromFile.getName()));
        }
        tasks.add(task);
      }
      studentLesson.updateTaskList(tasks);
    }
    currentCourse.setLessons(updatedLessons);

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
        pairs = new ArrayList<>();
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

  private class FileCreatedByUserListener extends VirtualFileAdapter {
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      if (myProject.isDisposed()) return;
      final VirtualFile createdFile = event.getFile();
      final VirtualFile taskDir = createdFile.getParent();
      final Course course = StudyTaskManager.getInstance(myProject).getCourse();
      if (course == null || !EduNames.STUDY.equals(course.getCourseMode())) {
        return;
      }
      if (taskDir != null && taskDir.getName().contains(EduNames.TASK)) {
        int taskIndex = EduUtils.getIndex(taskDir.getName(), EduNames.TASK);
        final VirtualFile lessonDir = taskDir.getParent();
        if (lessonDir != null && lessonDir.getName().contains(EduNames.LESSON)) {
          int lessonIndex = EduUtils.getIndex(lessonDir.getName(), EduNames.LESSON);
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
              //TODO: put to other steps as well
              task.getTaskFiles().put(name, taskFile);
            }
          }
        }
      }
    }
  }
}
