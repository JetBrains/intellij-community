package com.jetbrains.edu.learning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.edu.learning.course.*;
import com.jetbrains.edu.learning.stepic.StudyStepicConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StudyProjectGenerator {
  private static final Logger LOG = Logger.getInstance(StudyProjectGenerator.class.getName());
  private final List<SettingsListener> myListeners = ContainerUtil.newArrayList();
  private final File myCoursesDir = new File(PathManager.getConfigPath(), "courses");
  private static final String CACHE_NAME = "courseNames.txt";
  private List<CourseInfo> myCourses = new ArrayList<CourseInfo>();
  private CourseInfo mySelectedCourseInfo;

  public void setCourses(List<CourseInfo> courses) {
    myCourses = courses;
  }

  public void setSelectedCourse(@NotNull final CourseInfo courseName) {
    mySelectedCourseInfo = courseName;
  }

  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir) {
    final Course course = StudyStepicConnector.getCourse(mySelectedCourseInfo);
    if (course == null) return;
    flushCourse(course);
    course.init(false);
    final File courseDirectory = new File(myCoursesDir, course.getName());
    course.create(baseDir, courseDirectory, project);
    course.setCourseDirectory(new File(myCoursesDir, mySelectedCourseInfo.getName()).getAbsolutePath());
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    StudyTaskManager.getInstance(project).setCourse(course);
    ToolWindowManagerEx.getInstanceEx(project).addToolWindowManagerListener(new OpenFirstTaskListener(project, course));
  }

  private static class OpenFirstTaskListener extends ToolWindowManagerAdapter {
    private final Project myProject;
    private final Course myCourse;
    private boolean myInitialized = false;

    OpenFirstTaskListener(@NotNull final Project project, @NotNull final Course course) {
      myProject = project;
      myCourse = course;
    }

    public void stateChanged() {
      final AbstractProjectViewPane projectViewPane = ProjectView.getInstance(myProject).getCurrentProjectViewPane();
      if (projectViewPane == null || myInitialized) return;
      JTree tree = projectViewPane.getTree();
      if (tree == null) {
        return;
      }
      tree.updateUI();

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          LocalFileSystem.getInstance().refresh(false);
          final Lesson firstLesson = StudyUtils.getFirst(myCourse.getLessons());
          final Task firstTask = StudyUtils.getFirst(firstLesson.getTaskList());
          final VirtualFile taskDir = firstTask.getTaskDir(myProject);
          if (taskDir == null) return;
          final Map<String, TaskFile> taskFiles = firstTask.getTaskFiles();
          VirtualFile activeVirtualFile = null;
          for (Map.Entry<String, TaskFile> entry : taskFiles.entrySet()) {
            final String name = entry.getKey();
            final TaskFile taskFile = entry.getValue();
            final VirtualFile virtualFile = ((VirtualDirectoryImpl)taskDir).refreshAndFindChild(name);
            if (virtualFile != null) {
              FileEditorManager.getInstance(myProject).openFile(virtualFile, true);
              if (!taskFile.getAnswerPlaceholders().isEmpty()) {
                activeVirtualFile = virtualFile;
              }
            }
          }
          if (activeVirtualFile != null) {
            final PsiFile file = PsiManager.getInstance(myProject).findFile(activeVirtualFile);
            ProjectView.getInstance(myProject).select(file, activeVirtualFile, true);
          } else {
            String first = StudyUtils.getFirst(taskFiles.keySet());
            if (first != null) {
              NewVirtualFile firstFile = ((VirtualDirectoryImpl)taskDir).refreshAndFindChild(first);
              if (firstFile != null) {
                FileEditorManager.getInstance(myProject).openFile(firstFile, true);
              }
            }
          }
          myInitialized = true;
        }
      }, ModalityState.current(), new Condition() {
        @Override
        public boolean value(Object o) {
          return myProject.isDisposed();
        }
      });
    }
  }

  private void flushCourse(@NotNull final Course course) {
    final File courseDirectory = new File(myCoursesDir, course.getName());
    FileUtil.createDirectory(courseDirectory);
    flushCourseJson(course, courseDirectory);

    int lessonIndex = 1;
    for (Lesson lesson : course.lessons) {
      final File lessonDirectory = new File(courseDirectory, "lesson"+String.valueOf(lessonIndex));
      FileUtil.createDirectory(lessonDirectory);
      int taskIndex = 1;
      for (Task task : lesson.taskList) {
        final File taskDirectory = new File(lessonDirectory, "task" + String.valueOf(taskIndex));
        FileUtil.createDirectory(taskDirectory);
        for (Map.Entry<String, TaskFile> taskFileEntry : task.taskFiles.entrySet()) {
          final String name = taskFileEntry.getKey();
          final TaskFile taskFile = taskFileEntry.getValue();
          final File file = new File(taskDirectory, name);
          FileUtil.createIfDoesntExist(file);

          try {
            FileUtil.writeToFile(file, taskFile.text);
          }
          catch (IOException e) {
            LOG.error("ERROR copying file " + name);
          }
        }
        StudyLanguageManager languageManager = StudyUtils.getLanguageManager(course);
        if (languageManager == null) {
          LOG.info("Language manager is null for " + course.getLanguageById().getDisplayName());
          return;
        }
        final File testsFile = new File(taskDirectory, languageManager.getTestFileName());
        FileUtil.createIfDoesntExist(testsFile);
        try {
          FileUtil.writeToFile(testsFile, task.getTestsText());
        }
        catch (IOException e) {
          LOG.error("ERROR copying tests file");
        }
        final File taskText = new File(taskDirectory, "task.html");
        FileUtil.createIfDoesntExist(taskText);
        try {
          FileUtil.writeToFile(taskText, task.getText());
        }
        catch (IOException e) {
          LOG.error("ERROR copying tests file");
        }
        taskIndex += 1;
      }
      lessonIndex += 1;
    }
  }

  private static void flushCourseJson(@NotNull final Course course, @NotNull final File courseDirectory) {
    final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    final String json = gson.toJson(course);
    final File courseJson = new File(courseDirectory, "course.json");
    final FileOutputStream fileOutputStream;
    try {
      fileOutputStream = new FileOutputStream(courseJson);
      OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, "UTF-8");
      try {
        outputStreamWriter.write(json);
      }
      catch (IOException e) {
        Messages.showErrorDialog(e.getMessage(), "Failed to Generate Json");
        LOG.info(e);
      }
      finally {
        try {
          outputStreamWriter.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
    catch (FileNotFoundException e) {
      LOG.info(e);
    }
    catch (UnsupportedEncodingException e) {
      LOG.info(e);
    }
  }

  /**
   * Writes courses to cash file {@link StudyProjectGenerator#CACHE_NAME}
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public void flushCache() {
    File cashFile = new File(myCoursesDir, CACHE_NAME);
    PrintWriter writer = null;
    try {
      if (!cashFile.exists()) {
        final boolean created = cashFile.createNewFile();
        if (!created) {
          LOG.error("Cannot flush courses cache. Can't create " + CACHE_NAME + " file");
          return;
        }
      }
      writer = new PrintWriter(cashFile);
      for (CourseInfo courseInfo : myCourses) {
        String line = String
          .format("name=%s author=%s description=%s", courseInfo.getName(), courseInfo.getAuthor(),
                  courseInfo.getDescription());
        writer.println(line);
      }
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      StudyUtils.closeSilently(writer);
    }
  }

  /**
   * @return courses from memory or from cash file or parses course directory
   */
  public List<CourseInfo> getCourses() {
    if (!myCourses.isEmpty()) {
      return myCourses;
    }
    else {
      myCourses = StudyStepicConnector.getCourses();
      return myCourses;
    }
  }

  public void addSettingsStateListener(@NotNull SettingsListener listener) {
    myListeners.add(listener);
  }

  public interface SettingsListener {
    void stateChanged(ValidationResult result);
  }

  public void fireStateChanged(ValidationResult result) {
    for (SettingsListener listener : myListeners) {
      listener.stateChanged(result);
    }
  }
}
