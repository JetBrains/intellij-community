package com.jetbrains.edu.learning.courseGeneration;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyProjectComponent;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.stepic.CourseInfo;
import com.jetbrains.edu.stepic.EduStepicConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StudyProjectGenerator {
  private static final Logger LOG = Logger.getInstance(StudyProjectGenerator.class.getName());
  private final List<SettingsListener> myListeners = ContainerUtil.newArrayList();
  private final File myCoursesDir = new File(PathManager.getConfigPath(), "courses");
  private static final String CACHE_NAME = "courseNames.txt";
  private List<CourseInfo> myCourses = new ArrayList<CourseInfo>();
  private CourseInfo mySelectedCourseInfo;
  private static final Pattern CACHE_PATTERN = Pattern.compile("name=(.*) description=(.*) folder=(.*) (instructor=(.*))+");
  private static final String COURSE_META_FILE = "course.json";
  private static final String COURSE_NAME_ATTRIBUTE = "name";
  private static final String COURSE_DESCRIPTION = "description";
  public static final String AUTHOR_ATTRIBUTE = "authors";

  public void setCourses(List<CourseInfo> courses) {
    myCourses = courses;
  }

  public void setSelectedCourse(@NotNull final CourseInfo courseName) {
    mySelectedCourseInfo = courseName;
  }

  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir) {
    final Course course = getCourse();
    if (course == null) return;
    StudyTaskManager.getInstance(project).setCourse(course);
    ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              course.initCourse(false);
              final File courseDirectory = new File(myCoursesDir, course.getName());
              StudyGenerator.createCourse(course, baseDir, courseDirectory, project);
              course.setCourseDirectory(new File(myCoursesDir, mySelectedCourseInfo.getName()).getAbsolutePath());
              VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
              StudyProjectComponent.getInstance(project).registerStudyToolwindow(course);
              openFirstTask(course, project);

            }
          });
        }
      });
  }

  private Course getCourse() {
    Reader reader = null;
    try {
      final File courseFile = new File(new File(myCoursesDir, mySelectedCourseInfo.getName()), COURSE_META_FILE);
      if (courseFile.exists()) {
        reader = new InputStreamReader(new FileInputStream(courseFile), "UTF-8");
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        final Course course = gson.fromJson(reader, Course.class);
        course.initCourse(false);
        return course;
      }
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
    }
    finally {
      StudyUtils.closeSilently(reader);
    }
    final Course course = EduStepicConnector.getCourse(mySelectedCourseInfo);
    if (course != null) {
      flushCourse(course);
    }
    return course;
  }

  public static void openFirstTask(@NotNull final Course course, @NotNull final Project project) {
    LocalFileSystem.getInstance().refresh(false);
    final Lesson firstLesson = StudyUtils.getFirst(course.getLessons());
    final Task firstTask = StudyUtils.getFirst(firstLesson.getTaskList());
    final VirtualFile taskDir = firstTask.getTaskDir(project);
    if (taskDir == null) return;
    final Map<String, TaskFile> taskFiles = firstTask.getTaskFiles();
    VirtualFile activeVirtualFile = null;
    for (Map.Entry<String, TaskFile> entry : taskFiles.entrySet()) {
      final String name = entry.getKey();
      final TaskFile taskFile = entry.getValue();
      final VirtualFile virtualFile = ((VirtualDirectoryImpl)taskDir).refreshAndFindChild(name);
      if (virtualFile != null) {
        FileEditorManager.getInstance(project).openFile(virtualFile, true);
        if (!taskFile.getAnswerPlaceholders().isEmpty()) {
          activeVirtualFile = virtualFile;
        }
      }
    }
    if (activeVirtualFile != null) {
      final PsiFile file = PsiManager.getInstance(project).findFile(activeVirtualFile);
      ProjectView.getInstance(project).select(file, activeVirtualFile, true);
    } else {
      String first = StudyUtils.getFirst(taskFiles.keySet());
      if (first != null) {
        NewVirtualFile firstFile = ((VirtualDirectoryImpl)taskDir).refreshAndFindChild(first);
        if (firstFile != null) {
          FileEditorManager.getInstance(project).openFile(firstFile, true);
        }
      }
    }
  }

  public void flushCourse(@NotNull final Course course) {
    final File courseDirectory = new File(myCoursesDir, course.getName());
    FileUtil.createDirectory(courseDirectory);
    flushCourseJson(course, courseDirectory);

    int lessonIndex = 1;
    for (Lesson lesson : course.getLessons()) {
      final File lessonDirectory = new File(courseDirectory, EduNames.LESSON + String.valueOf(lessonIndex));
      FileUtil.createDirectory(lessonDirectory);
      int taskIndex = 1;
      for (Task task : lesson.taskList) {
        final File taskDirectory = new File(lessonDirectory, EduNames.TASK + String.valueOf(taskIndex));
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
        final Map<String, String> testsText = task.getTestsText();
        for (Map.Entry<String, String> entry : testsText.entrySet()) {
          final File testsFile = new File(taskDirectory, entry.getKey());
          FileUtil.createIfDoesntExist(testsFile);
          try {
              FileUtil.writeToFile(testsFile, entry.getValue());
          }
          catch (IOException e) {
            LOG.error("ERROR copying tests file");
          }
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
   * Writes courses to cache file {@link StudyProjectGenerator#CACHE_NAME}
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public void flushCache() {
    File cacheFile = new File(myCoursesDir, CACHE_NAME);
    PrintWriter writer = null;
    try {
      if (!cacheFile.exists()) {
        final boolean created = cacheFile.createNewFile();
        if (!created) {
          LOG.error("Cannot flush courses cache. Can't create " + CACHE_NAME + " file");
          return;
        }
      }
      writer = new PrintWriter(cacheFile);
      for (CourseInfo courseInfo : myCourses) {
        final List<CourseInfo.Instructor> instructors = courseInfo.getInstructors();
        StringBuilder builder = new StringBuilder("name=").append(courseInfo.getName()).append(" ").append("description=").
          append(courseInfo.getDescription()).append(" ").append("folder=").append(courseInfo.getName()).append(" ");
        for (CourseInfo.Instructor instructor : instructors) {
          builder.append("instructor=").append(instructor.getName()).append(" ");
        }
        writer.println(builder.toString());
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

  public List<CourseInfo> getCourses(boolean force) {
    if (myCoursesDir.exists()) {
      File cacheFile = new File(myCoursesDir, CACHE_NAME);
      if (cacheFile.exists()) {
        myCourses = getCoursesFromCache(cacheFile);
      }
    }
    if (force || myCourses.isEmpty()) {
      myCourses = EduStepicConnector.getCourses();
      flushCache();
    }
    return myCourses;
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

  private static List<CourseInfo> getCoursesFromCache(@NotNull final File cacheFile) {
    List<CourseInfo> courses = new ArrayList<CourseInfo>();
    try {
      final FileInputStream inputStream = new FileInputStream(cacheFile);
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        try {
          String line;
          while ((line = reader.readLine()) != null) {
            Matcher matcher = CACHE_PATTERN.matcher(line);
            if (matcher.matches()) {
              String courseName = matcher.group(1);
              final CourseInfo courseInfo = new CourseInfo();
              courseInfo.setName(courseName);

              courseInfo.setDescription(matcher.group(2));
              courses.add(courseInfo);

              final int groupCount = matcher.groupCount();
              final ArrayList<CourseInfo.Instructor> instructors = new ArrayList<CourseInfo.Instructor>();
              for (int i = 5; i <= groupCount; i++) {
                instructors.add(new CourseInfo.Instructor(matcher.group(i)));
              }
              courseInfo.setInstructors(instructors);
            }
          }
        }
        catch (IOException e) {
          LOG.error(e.getMessage());
        }
        finally {
          StudyUtils.closeSilently(reader);
        }
      } finally {
        StudyUtils.closeSilently(inputStream);
      }
    }
    catch (FileNotFoundException e) {
      LOG.error(e.getMessage());
    }
    return courses;
  }
  /**
   * Adds course from zip archive to courses
   *
   * @return added course name or null if course is invalid
   */
  @Nullable
  public CourseInfo addLocalCourse(String zipFilePath) {
    File file = new File(zipFilePath);
    try {
      String fileName = file.getName();
      String unzippedName = fileName.substring(0, fileName.indexOf("."));
      File courseDir = new File(myCoursesDir, unzippedName);
      ZipUtil.unzip(null, courseDir, file, null, null, true);
      CourseInfo courseName = addCourse(myCourses, courseDir);
      flushCache();
      return courseName;
    }
    catch (IOException e) {
      LOG.error("Failed to unzip course archive");
      LOG.error(e);
    }
    return null;
  }

  /**
   * Adds course to courses specified in params
   *
   *
   * @param courses
   * @param courseDir must be directory containing course file
   * @return added course name or null if course is invalid
   */
  @Nullable
  private static CourseInfo addCourse(List<CourseInfo> courses, File courseDir) {
    if (courseDir.isDirectory()) {
      File[] courseFiles = courseDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.equals(COURSE_META_FILE);
        }
      });
      if (courseFiles.length != 1) {
        LOG.info("User tried to add course with more than one or without course files");
        return null;
      }
      File courseFile = courseFiles[0];
      CourseInfo courseInfo = getCourseInfo(courseFile);
      if (courseInfo != null) {
        courses.add(courseInfo);
      }
      return courseInfo;
    }
    return null;
  }
  /**
   * Parses course json meta file and finds course name
   *
   * @return information about course or null if course file is invalid
   */
  @Nullable
  private static CourseInfo getCourseInfo(File courseFile) {
    CourseInfo courseInfo = null;
    BufferedReader reader = null;
    try {
      if (courseFile.getName().equals(COURSE_META_FILE)) {
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(courseFile), "UTF-8"));
        JsonReader r = new JsonReader(reader);
        JsonParser parser = new JsonParser();
        JsonElement el = parser.parse(r);
        String courseName = el.getAsJsonObject().get(COURSE_NAME_ATTRIBUTE).getAsString();
        String courseDescription = el.getAsJsonObject().get(COURSE_DESCRIPTION).getAsString();
        JsonArray courseAuthors = el.getAsJsonObject().get(AUTHOR_ATTRIBUTE).getAsJsonArray();
        courseInfo = new CourseInfo();
        courseInfo.setName(courseName);
        courseInfo.setDescription(courseDescription);
        courseInfo.setName(courseFile.getParent());
        final ArrayList<CourseInfo.Instructor> instructors = new ArrayList<CourseInfo.Instructor>();
        for (JsonElement author : courseAuthors) {
          final String authorAsString = author.getAsString();
          instructors.add(new CourseInfo.Instructor(authorAsString));
        }
        courseInfo.setInstructors(instructors);
      }
    }
    catch (Exception e) {
      //error will be shown in UI
    }
    finally {
      StudyUtils.closeSilently(reader);
    }
    return courseInfo;
  }
}
