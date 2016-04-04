package com.jetbrains.edu.learning.courseGeneration;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
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
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyProjectComponent;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.stepic.CourseInfo;
import com.jetbrains.edu.stepic.EduStepicConnector;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StudyProjectGenerator {
  private static final Logger LOG = Logger.getInstance(StudyProjectGenerator.class.getName());
  private final List<SettingsListener> myListeners = ContainerUtil.newArrayList();
  protected static final File ourCoursesDir = new File(PathManager.getConfigPath(), "courses");
  private static final String CACHE_NAME = "courseNames.txt";
  private List<CourseInfo> myCourses = new ArrayList<>();
  protected CourseInfo mySelectedCourseInfo;
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
    if (course == null) {
      LOG.warn("Course is null");
      return;
    }
    StudyTaskManager.getInstance(project).setCourse(course);
    ApplicationManager.getApplication().invokeLater(
      () -> DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND,
                                                    () -> ApplicationManager.getApplication().runWriteAction(() -> {
                                                      course.initCourse(false);
                                                      final File courseDirectory = new File(ourCoursesDir, course.getName());
                                                      StudyGenerator.createCourse(course, baseDir, courseDirectory, project);
                                                      course.setCourseDirectory(new File(ourCoursesDir, mySelectedCourseInfo.getName()).getAbsolutePath());
                                                      VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
                                                      StudyProjectComponent.getInstance(project).registerStudyToolWindow(course);
                                                      openFirstTask(course, project);
                                                    })));
  }

  protected Course getCourse() {
    Reader reader = null;
    try {
      final File courseFile = new File(new File(ourCoursesDir, mySelectedCourseInfo.getName()), EduNames.COURSE_META_FILE);
      if (courseFile.exists()) {
        reader = new InputStreamReader(new FileInputStream(courseFile), "UTF-8");
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        final Course course = gson.fromJson(reader, Course.class);
        course.initCourse(false);
        return course;
      }
    }
    catch (FileNotFoundException | UnsupportedEncodingException e) {
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
      FileEditorManager.getInstance(project).openFile(activeVirtualFile, true);
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
    final File courseDirectory = new File(ourCoursesDir, course.getName());
    FileUtil.createDirectory(courseDirectory);
    flushCourseJson(course, courseDirectory);

    int lessonIndex = 1;
    for (Lesson lesson : course.getLessons()) {
      if (lesson.getName().equals(EduNames.PYCHARM_ADDITIONAL)) {
        flushAdditionalFiles(courseDirectory, lesson);
      }
      else {
        final File lessonDirectory = new File(courseDirectory, EduNames.LESSON + String.valueOf(lessonIndex));
        flushLesson(lessonDirectory, lesson);
        lessonIndex += 1;
      }
    }
  }

  private static void flushAdditionalFiles(File courseDirectory, Lesson lesson) {
    final List<Task> taskList = lesson.getTaskList();
    if (taskList.size() != 1) return;
    final Task task = taskList.get(0);
    for (Map.Entry<String, String> entry : task.getTestsText().entrySet()) {
      final String name = entry.getKey();
      final String text = entry.getValue();
      final File file = new File(courseDirectory, name);
      FileUtil.createIfDoesntExist(file);
      try {
        if (EduUtils.isImage(name)) {
          FileUtil.writeToFile(file, Base64.decodeBase64(text));
        }
        else {
          FileUtil.writeToFile(file, text);
        }
      }
      catch (IOException e) {
        LOG.error("ERROR copying file " + name);
      }
    }
  }

  public static void flushLesson(@NotNull final File lessonDirectory, @NotNull final Lesson lesson) {
    FileUtil.createDirectory(lessonDirectory);
    int taskIndex = 1;
    for (Task task : lesson.taskList) {
      final File taskDirectory = new File(lessonDirectory, EduNames.TASK + String.valueOf(taskIndex));
      flushTask(task, taskDirectory);
      taskIndex += 1;
    }
  }

  public static void flushTask(@NotNull final Task task, @NotNull final File taskDirectory) {
    FileUtil.createDirectory(taskDirectory);
    for (Map.Entry<String, TaskFile> taskFileEntry : task.taskFiles.entrySet()) {
      final String name = taskFileEntry.getKey();
      final TaskFile taskFile = taskFileEntry.getValue();
      final File file = new File(taskDirectory, name);
      FileUtil.createIfDoesntExist(file);

      try {
        if (EduUtils.isImage(taskFile.name)) {
          FileUtil.writeToFile(file, Base64.decodeBase64(taskFile.text));
        }
        else {
          FileUtil.writeToFile(file, taskFile.text);
        }

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
  }

  private static void flushCourseJson(@NotNull final Course course, @NotNull final File courseDirectory) {
    final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    final String json = gson.toJson(course);
    final File courseJson = new File(courseDirectory, EduNames.COURSE_META_FILE);
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
    catch (FileNotFoundException | UnsupportedEncodingException e) {
      LOG.info(e);
    }
  }

  /**
   * Writes courses to cache file {@link StudyProjectGenerator#CACHE_NAME}
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static void flushCache(List<CourseInfo> courses) {
    File cacheFile = new File(ourCoursesDir, CACHE_NAME);
    PrintWriter writer = null;
    try {
      if (!createCacheFile(cacheFile)) return;
      Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

      writer = new PrintWriter(cacheFile);
      for (CourseInfo courseInfo : courses) {
        final String json = gson.toJson(courseInfo);
        writer.println(json);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      StudyUtils.closeSilently(writer);
    }
  }

  private static boolean createCacheFile(File cacheFile) throws IOException {
    if (!ourCoursesDir.exists()) {
      final boolean created = ourCoursesDir.mkdirs();
      if (!created) {
        LOG.error("Cannot flush courses cache. Can't create courses directory");
        return false;
      }
    }
    if (!cacheFile.exists()) {
      final boolean created = cacheFile.createNewFile();
      if (!created) {
        LOG.error("Cannot flush courses cache. Can't create " + CACHE_NAME + " file");
        return false;
      }
    }
    return true;
  }

  public List<CourseInfo> getCourses(boolean force) {
    if (ourCoursesDir.exists()) {
      myCourses = getCoursesFromCache();
    }
    if (force || myCourses.isEmpty()) {
      myCourses = EduStepicConnector.getCourses();
      flushCache(myCourses);
    }
    if (myCourses.isEmpty()) {
      myCourses = getBundledIntro();
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

  public static List<CourseInfo> getBundledIntro() {
    final File introCourse = new File(ourCoursesDir, "Introduction to Python");
    if (introCourse.exists()) {
      final CourseInfo courseInfo = getCourseInfo(introCourse);

      return Collections.singletonList(courseInfo);
    }
    return Collections.emptyList();
  }

  public static List<CourseInfo> getCoursesFromCache() {
    List<CourseInfo> courses = new ArrayList<>();
    final File cacheFile = new File(ourCoursesDir, CACHE_NAME);
    if (!cacheFile.exists()) {
      return courses;
    }
    try {
      final FileInputStream inputStream = new FileInputStream(cacheFile);
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        try {
          String line;
          while ((line = reader.readLine()) != null) {
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            final CourseInfo courseInfo = gson.fromJson(line, CourseInfo.class);
            courses.add(courseInfo);
          }
        }
        catch (IOException | JsonSyntaxException e) {
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
      File courseDir = new File(ourCoursesDir, unzippedName);
      ZipUtil.unzip(null, courseDir, file, null, null, true);
      CourseInfo courseName = addCourse(myCourses, courseDir);
      flushCache(myCourses);
      if (courseName != null && !courseName.getName().equals(unzippedName)) {
        courseDir.renameTo(new File(ourCoursesDir, courseName.getName()));
        courseDir.delete();
      }
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
      File[] courseFiles = courseDir.listFiles((dir, name) -> {
        return name.equals(EduNames.COURSE_META_FILE);
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
    if (courseFile.isDirectory()) {
      File[] courseFiles = courseFile.listFiles((dir, name) -> {
        return name.equals(EduNames.COURSE_META_FILE);
      });
      if (courseFiles.length != 1) {
        LOG.info("More than one or without course files");
        return null;
      }
      courseFile = courseFiles[0];
    }
    CourseInfo courseInfo = null;
    BufferedReader reader = null;
    try {
      if (courseFile.getName().equals(EduNames.COURSE_META_FILE)) {
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
        final ArrayList<CourseInfo.Author> authors = new ArrayList<>();
        for (JsonElement author : courseAuthors) {
          final JsonObject authorAsJsonObject = author.getAsJsonObject();
          authors.add(new CourseInfo.Author(authorAsJsonObject.get("first_name").getAsString(), authorAsJsonObject.get("last_name").getAsString()));
        }
        courseInfo.setAuthors(authors);
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
