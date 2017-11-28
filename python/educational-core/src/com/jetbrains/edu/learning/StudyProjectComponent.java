package com.jetbrains.edu.learning;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.edu.learning.actions.StudyActionWithShortcut;
import com.jetbrains.edu.learning.actions.StudyNextWindowAction;
import com.jetbrains.edu.learning.actions.StudyPrevWindowAction;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseGeneration.StudyGenerator;
import com.jetbrains.edu.learning.editor.StudyEditorFactoryListener;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.ui.StudyStepicUserWidget;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import com.jetbrains.edu.learning.ui.StudyToolWindowFactory;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.jetbrains.edu.learning.StudyUtils.execCancelable;
import static com.jetbrains.edu.learning.StudyUtils.navigateToStep;
import static com.jetbrains.edu.learning.stepic.EduStepicNames.STEP_ID;


public class StudyProjectComponent implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(StudyProjectComponent.class.getName());
  private final Project myProject;
  private FileCreatedByUserListener myListener;
  private final Map<Keymap, List<Pair<String, String>>> myDeletedShortcuts = new HashMap<>();
  private MessageBusConnection myBusConnection;

  private StudyProjectComponent(@NotNull final Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
    // Check if user has javafx lib in his JDK. Now bundled JDK doesn't have this lib inside.
    if (StudyUtils.hasJavaFx()) {
      Platform.setImplicitExit(false);
    }

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
      () -> {
        Course course = StudyTaskManager.getInstance(myProject).getCourse();
        if (course == null) {
          LOG.warn("Opened project is with null course");
          return;
        }

        if (!course.isAdaptive() && !course.isUpToDate()) {
          updateAvailable(course);
        }

        StudyUtils.registerStudyToolWindow(course, myProject);
        addStepicWidget();
        selectStep(course);

        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
            UISettings instance = UISettings.getInstance();
            instance.setHideToolStripes(false);
            instance.fireUISettingsChanged();
            registerShortcuts();
            EduUsagesCollector.projectTypeOpened(course.isAdaptive() ? EduNames.ADAPTIVE : EduNames.STUDY);
          }));
      }
    );

    myBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myBusConnection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        final StudyToolWindow toolWindow = StudyUtils.getStudyToolWindow(myProject);
        if (toolWindow != null) {
          toolWindow.updateFonts(myProject);
        }
      }
    });
  }

  private void addStepicWidget() {
    StudyStepicUserWidget widget = StudyUtils.getStepicWidget();
    if (widget == null) {
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
      statusBar.addWidget(new StudyStepicUserWidget(), "before Position");
    }
    else {
      widget.update();
    }
  }

  private void selectStep(@NotNull Course course) {
    int stepId = PropertiesComponent.getInstance().getInt(STEP_ID, 0);
    if (stepId != 0) {
      navigateToStep(myProject, course, stepId);
    }
  }

  private void updateAvailable(Course course) {
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

  private void registerShortcuts() {
    StudyToolWindow window = StudyUtils.getStudyToolWindow(myProject);
    if (window != null) {
      List<AnAction> actionsOnToolbar = window.getActions(true);
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
  }

  private void updateCourse() {
    final Course currentCourse = StudyTaskManager.getInstance(myProject).getCourse();
    if (currentCourse == null || !(currentCourse instanceof RemoteCourse)) return;
    final Course course = EduStepicConnector.getCourse(myProject, (RemoteCourse)currentCourse);
    if (course == null) return;
    course.initCourse(false);

    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById());
    if (configurator == null) {
      LOG.info("EduPluginConfigurator not found for language " + course.getLanguageById().getDisplayName());
      return;
    }

    final ArrayList<Lesson> updatedLessons = new ArrayList<>();

    int lessonIndex = 0;
    for (Lesson lesson : course.getLessons(true)) {
      lessonIndex += 1;
      Lesson studentLesson = currentCourse.getLesson(lesson.getId());
      final String lessonDirName = EduNames.LESSON + String.valueOf(lessonIndex);

      final VirtualFile baseDir = myProject.getBaseDir();
      final VirtualFile lessonDir = baseDir.findChild(lessonDirName);
      if (lessonDir == null) {
        try {
          StudyGenerator.createLesson(lesson, baseDir);
        }
        catch (IOException e) {
          LOG.error("Failed to create lesson");
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
        final Task studentTask = studentLesson.getTask(task.getStepId());
        if (studentTask != null && StudyStatus.Solved.equals(studentTask.getStatus())) {
          studentTask.setIndex(index);
          tasks.add(studentTask);
          continue;
        }
        task.initTask(studentLesson, false);
        task.setIndex(index);

        final String taskDirName = EduNames.TASK + String.valueOf(index);
        final VirtualFile taskDir = lessonDir.findChild(taskDirName);

        if (taskDir != null) return;
        try {
          StudyGenerator.createTask(task, lessonDir);
        }
        catch (IOException e) {
          LOG.error("Failed to create task");
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
            myListener = new FileCreatedByUserListener();
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
    if (myBusConnection != null) {
      myBusConnection.disconnect();
    }
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

  private class FileCreatedByUserListener implements VirtualFileListener {
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      if (myProject.isDisposed()) return;
      final VirtualFile createdFile = event.getFile();
      final VirtualFile taskDir = StudyUtils.getTaskDir(createdFile);
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
              final String name = FileUtil.getRelativePath(taskDir.getPath(), createdFile.getPath(), '/');
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
