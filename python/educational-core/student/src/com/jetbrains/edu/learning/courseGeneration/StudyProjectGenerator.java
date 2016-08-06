package com.jetbrains.edu.learning.courseGeneration;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.stepic.CourseInfo;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

import static com.jetbrains.edu.learning.StudyUtils.execCancelable;

public class StudyProjectGenerator {
  public static final String AUTHOR_ATTRIBUTE = "authors";
  public static final String LANGUAGE_ATTRIBUTE = "language";
  public static final String ADAPTIVE_COURSE_PREFIX = "__AdaptivePyCharmPython__";
  public static final File OUR_COURSES_DIR = new File(PathManager.getConfigPath(), "courses");
  private static final Logger LOG = Logger.getInstance(StudyProjectGenerator.class.getName());
  private static final String COURSE_NAME_ATTRIBUTE = "name";
  private static final String COURSE_DESCRIPTION = "description";
  private static final String CACHE_NAME = "courseNames.txt";
  private final List<SettingsListener> myListeners = ContainerUtil.newArrayList();
  @Nullable public StepicUser myUser;
  private List<CourseInfo> myCourses = new ArrayList<>();
  private List<Integer> myEnrolledCoursesIds = new ArrayList<>();
  protected CourseInfo mySelectedCourseInfo;

  public void setCourses(List<CourseInfo> courses) {
    myCourses = courses;
  }

  public boolean isLoggedIn() {
    return myUser != null && !StringUtil.isEmptyOrSpaces(myUser.getPassword()) && !StringUtil.isEmptyOrSpaces(myUser.getEmail());
  }

  public void setEnrolledCoursesIds(@NotNull final List<Integer> coursesIds) {
    myEnrolledCoursesIds = coursesIds;
  }

  @NotNull
  public List<Integer> getEnrolledCoursesIds() {
    return myEnrolledCoursesIds;
  }

  public void setSelectedCourse(@NotNull final CourseInfo courseName) {
    mySelectedCourseInfo = courseName;
  }

  public CourseInfo getSelectedCourseInfo() {
    return mySelectedCourseInfo;
  }

  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir) {
    if (myUser != null) {
      StudyTaskManager.getInstance(project).setUser(myUser);
    }
    final Course course = getCourse(project);
    if (course == null) {
      LOG.warn("Course is null");
      Messages.showWarningDialog("Some problems occurred while creating the course", "Error in Course Creation");
      return;
    }
    final File courseDirectory = StudyUtils.getCourseDirectory(project, course);
    StudyTaskManager.getInstance(project).setCourse(course);
    ApplicationManager.getApplication().runWriteAction(() -> {
      StudyGenerator.createCourse(course, baseDir, courseDirectory, project);
      course.setCourseDirectory(courseDirectory.getAbsolutePath());
      VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
      StudyUtils.registerStudyToolWindow(course, project);
      openFirstTask(course, project);
      EduUsagesCollector.projectTypeCreated(course.isAdaptive() ? EduNames.ADAPTIVE : EduNames.STUDY);
    });
  }

  @Nullable
  public Course getCourse(@NotNull final Project project) {

    final File courseFile = new File(new File(OUR_COURSES_DIR, mySelectedCourseInfo.getName()), EduNames.COURSE_META_FILE);
    if (courseFile.exists()) {
      final Course course = readCourseFromCache(courseFile, false);
      if (course != null && course.isUpToDate()) {
        return course;
      }
      return getCourseFromStepic(project);
    }
    else if (myUser != null) {
      final File adaptiveCourseFile = new File(new File(OUR_COURSES_DIR, ADAPTIVE_COURSE_PREFIX +
                                                                         mySelectedCourseInfo.getName() + "_" +
                                                                         myUser.getEmail()), EduNames.COURSE_META_FILE);
      if (adaptiveCourseFile.exists()) {
        return readCourseFromCache(adaptiveCourseFile, true);
      }
    }
    return getCourseFromStepic(project);
  }

  private Course getCourseFromStepic(@NotNull Project project) {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
      return execCancelable(() -> {
        final Course course = EduStepicConnector.getCourse(project, mySelectedCourseInfo);
        if (course != null) {
          flushCourse(project, course);
          course.initCourse(false);
        }
        return course;
      });
    }, "Creating Course", true, project);
  }

  @Nullable
  private static Course readCourseFromCache(@NotNull File courseFile, boolean isAdaptive) {
    Reader reader = null;
    try {
      reader = new InputStreamReader(new FileInputStream(courseFile), "UTF-8");
      Gson gson =
        new GsonBuilder().registerTypeAdapter(Course.class, new StudySerializationUtils.Json.CourseTypeAdapter(courseFile)).create();
      final Course course = gson.fromJson(reader, Course.class);
      course.initCourse(isAdaptive);
      return course;
    }
    catch (UnsupportedEncodingException e) {
      LOG.warn(e.getMessage());
    }
    catch (FileNotFoundException e) {
      LOG.warn(e.getMessage());
    }
    finally {
      StudyUtils.closeSilently(reader);
    }
    return null;
  }

  public static void openFirstTask(@NotNull final Course course, @NotNull final Project project) {
    LocalFileSystem.getInstance().refresh(false);
    final Lesson firstLesson = StudyUtils.getFirst(course.getLessons());
    if (firstLesson == null) return;
    final Task firstTask = StudyUtils.getFirst(firstLesson.getTaskList());
    if (firstTask == null) return;
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
      VirtualFile finalActiveVirtualFile = activeVirtualFile;
      StartupManager.getInstance(project).registerPostStartupActivity(() -> {
        final PsiFile file = PsiManager.getInstance(project).findFile(finalActiveVirtualFile);
        ProjectView.getInstance(project).select(file, finalActiveVirtualFile, false);
        final FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(finalActiveVirtualFile);
        if (editors.length == 0) {
          return;
        }
        final FileEditor studyEditor = editors[0];
        if (studyEditor instanceof StudyEditor) {
          StudyUtils.selectFirstAnswerPlaceholder((StudyEditor)studyEditor, project);
        }
      });
      FileEditorManager.getInstance(project).openFile(finalActiveVirtualFile, true);
    }
    else {
      String first = StudyUtils.getFirst(taskFiles.keySet());
      if (first != null) {
        NewVirtualFile firstFile = ((VirtualDirectoryImpl)taskDir).refreshAndFindChild(first);
        if (firstFile != null) {
          FileEditorManager.getInstance(project).openFile(firstFile, true);
        }
      }
    }
  }

  public static void flushCourse(@NotNull final Project project, @NotNull final Course course) {
    final File courseDirectory = StudyUtils.getCourseDirectory(project, course);
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
      if (testsFile.exists()) {
        FileUtil.delete(testsFile);
      }
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

  public static void flushCourseJson(@NotNull final Course course, @NotNull final File courseDirectory) {
    final Gson gson = new GsonBuilder().setPrettyPrinting().
      excludeFieldsWithoutExposeAnnotation().create();
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
    flushCache(courses, true);
  }

  public static void flushCache(List<CourseInfo> courses, boolean preserveOld) {
    File cacheFile = new File(OUR_COURSES_DIR, CACHE_NAME);
    PrintWriter writer = null;
    try {
      if (!createCacheFile(cacheFile)) return;
      Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

      final Set<CourseInfo> courseInfos = new HashSet<>();
      courseInfos.addAll(courses);
      if (preserveOld) {
        courseInfos.addAll(getCoursesFromCache());
      }

      writer = new PrintWriter(cacheFile, "UTF-8");
      try {
        for (CourseInfo courseInfo : courseInfos) {
          final String json = gson.toJson(courseInfo);
          writer.println(json);
        }
      }
      finally {
        StudyUtils.closeSilently(writer);
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
    if (!OUR_COURSES_DIR.exists()) {
      final boolean created = OUR_COURSES_DIR.mkdirs();
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

  // Supposed to be called under progress
  public List<CourseInfo> getCourses(boolean force) {
    if (OUR_COURSES_DIR.exists()) {
      myCourses = getCoursesFromCache();
    }
    if (force || myCourses.isEmpty()) {
      myCourses = execCancelable(EduStepicConnector::getCourses);
      flushCache(myCourses);
    }
    if (myCourses.isEmpty()) {
      myCourses = getBundledIntro();
    }
    sortCourses(myCourses);
    return myCourses;
  }

  public void sortCourses(List<CourseInfo> result) {
    // sort courses so as to have non-adaptive courses in the beginning of the list
    Collections.sort(result, (c1, c2) -> {
      if (mySelectedCourseInfo != null) {
        if (mySelectedCourseInfo.equals(c1)) {
          return -1;
        }
        if (mySelectedCourseInfo.equals(c2)) {
          return 1;
        }
      }
      if ((c1.isAdaptive() && c2.isAdaptive()) || (!c1.isAdaptive() && !c2.isAdaptive())) {
        return 0;
      }
      return c1.isAdaptive() ? 1 : -1;
    });
  }

  @NotNull
  public List<CourseInfo> getCoursesUnderProgress(boolean force, @NotNull final String progressTitle, @NotNull final Project project) {
    try {
      return ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(() -> {
          ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
          return getCourses(force);
        }, progressTitle, true, project);
    }
    catch (RuntimeException e) {
      return Collections.singletonList(CourseInfo.INVALID_COURSE);
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

  public static List<CourseInfo> getBundledIntro() {
    final File introCourse = new File(OUR_COURSES_DIR, "Introduction to Python");
    if (introCourse.exists()) {
      final CourseInfo courseInfo = getCourseInfo(introCourse);

      return Collections.singletonList(courseInfo);
    }
    return Collections.emptyList();
  }

  public static List<CourseInfo> getCoursesFromCache() {
    List<CourseInfo> courses = new ArrayList<>();
    final File cacheFile = new File(OUR_COURSES_DIR, CACHE_NAME);
    if (!cacheFile.exists()) {
      return courses;
    }
    try {
      final FileInputStream inputStream = new FileInputStream(cacheFile);
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
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
      }
      catch (UnsupportedEncodingException e) {
        LOG.error(e.getMessage());
      }
      finally {
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
      File courseDir = new File(OUR_COURSES_DIR, unzippedName);
      ZipUtil.unzip(null, courseDir, file, null, null, true);
      CourseInfo courseName = addCourse(myCourses, courseDir);
      flushCache(myCourses);
      if (courseName != null && !courseName.getName().equals(unzippedName)) {
        //noinspection ResultOfMethodCallIgnored
        File dest = new File(OUR_COURSES_DIR, courseName.getName());
        if (dest.exists()) {
          FileUtil.delete(dest);
        }
        courseDir.renameTo(dest);
        //noinspection ResultOfMethodCallIgnored
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
   * @param courses
   * @param courseDir must be directory containing course file
   * @return added course name or null if course is invalid
   */
  @Nullable
  private static CourseInfo addCourse(List<CourseInfo> courses, File courseDir) {
    if (courseDir.isDirectory()) {
      File[] courseFiles = courseDir.listFiles((dir, name) -> name.equals(EduNames.COURSE_META_FILE));
      if (courseFiles == null || courseFiles.length != 1) {
        LOG.info("User tried to add course with more than one or without course files");
        return null;
      }
      File courseFile = courseFiles[0];
      CourseInfo courseInfo = getCourseInfo(courseFile);
      if (courseInfo != null) {
        courses.add(0, courseInfo);
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
      File[] courseFiles = courseFile.listFiles((dir, name) -> name.equals(EduNames.COURSE_META_FILE));
      if (courseFiles == null || courseFiles.length != 1) {
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
        String language = el.getAsJsonObject().get(LANGUAGE_ATTRIBUTE).getAsString();
        courseInfo = new CourseInfo();
        courseInfo.setName(courseName);
        courseInfo.setDescription(courseDescription);
        courseInfo.setType("pycharm " + language);
        final ArrayList<StepicUser> authors = new ArrayList<>();
        for (JsonElement author : courseAuthors) {
          final JsonObject authorAsJsonObject = author.getAsJsonObject();
          final StepicUser stepicUser = new StepicUser();
          stepicUser.setFirstName(authorAsJsonObject.get("first_name").getAsString());
          stepicUser.setLastName(authorAsJsonObject.get("last_name").getAsString());
          authors.add(stepicUser);
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
