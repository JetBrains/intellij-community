package com.jetbrains.python.edu;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.lang.javascript.boilerplate.GithubDownloadUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.templates.github.GeneratorException;
import com.intellij.platform.templates.github.ZipUtil;
import com.jetbrains.python.edu.course.Course;
import com.jetbrains.python.edu.course.CourseInfo;
import com.jetbrains.python.edu.ui.StudyNewProjectPanel;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import icons.StudyIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class StudyDirectoryProjectGenerator extends PythonProjectGenerator implements DirectoryProjectGenerator {
  private static final Logger LOG = Logger.getInstance(StudyDirectoryProjectGenerator.class.getName());
  private static final String REPO_URL = "https://github.com/JetBrains/pycharm-courses/archive/master.zip";
  private static final String USER_NAME = "PyCharm";
  private static final String COURSE_META_FILE = "course.json";
  private static final String COURSE_NAME_ATTRIBUTE = "name";
  private static final Pattern CACHE_PATTERN = Pattern.compile("(name=(.*)) (path=(.*course.json)) (author=(.*)) (description=(.*))");
  private static final String REPOSITORY_NAME = "pycharm-courses";
  public static final String AUTHOR_ATTRIBUTE = "author";
  private final File myCoursesDir = new File(PathManager.getConfigPath(), "courses");
  private static final String CACHE_NAME = "courseNames.txt";
  private Map<CourseInfo, File> myCourses = new HashMap<CourseInfo, File>();
  private File mySelectedCourseFile;
  private Project myProject;
  public ValidationResult myValidationResult = new ValidationResult("selected course is not valid");

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Study project";
  }


  public void setCourses(Map<CourseInfo, File> courses) {
    myCourses = courses;
  }

  /**
   * Finds selected course in courses by name.
   *
   * @param courseName name of selected course
   */
  public void setSelectedCourse(@NotNull CourseInfo courseName) {
    File courseFile = myCourses.get(courseName);
    if (courseFile == null) {
      LOG.error("invalid course in list");
    }
    mySelectedCourseFile = courseFile;
  }

  /**
   * Adds course to courses specified in params
   *
   * @param courseDir must be directory containing course file
   * @return added course name or null if course is invalid
   */
  @Nullable
  private static CourseInfo addCourse(Map<CourseInfo, File> courses, File courseDir) {
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
        courses.put(courseInfo, courseFile);
      }
      return courseInfo;
    }
    return null;
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

  @Nullable
  @Override
  public Object showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
    return null;
  }

  @Nullable
  @Override
  public Icon getLogo() {
    return StudyIcons.Playground;
  }


  @Override
  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                              @Nullable Object settings, @NotNull Module module) {

    myProject = project;
    Reader reader = null;
    try {
      reader = new InputStreamReader(new FileInputStream(mySelectedCourseFile));
      Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
      Course course = gson.fromJson(reader, Course.class);
      course.init(false);
      course.create(baseDir, new File(mySelectedCourseFile.getParent()));
      course.setResourcePath(mySelectedCourseFile.getAbsolutePath());
      VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
      StudyTaskManager.getInstance(project).setCourse(course);
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    finally {
      StudyUtils.closeSilently(reader);
    }
  }

  /**
   * Downloads courses from {@link com.jetbrains.python.edu.StudyDirectoryProjectGenerator#REPO_URL}
   * and unzips them into {@link com.jetbrains.python.edu.StudyDirectoryProjectGenerator#myCoursesDir}
   */

  public void downloadAndUnzip(boolean needProgressBar) {
    File outputFile = new File(PathManager.getConfigPath(), "courses.zip");
    try {
      if (!needProgressBar) {
        GithubDownloadUtil.downloadAtomically(null, REPO_URL,
                                              outputFile, USER_NAME, REPOSITORY_NAME);
      }
      else {
        GithubDownloadUtil.downloadContentToFileWithProgressSynchronously(myProject, REPO_URL, "downloading courses", outputFile, USER_NAME,
                                                                          REPOSITORY_NAME, false);
      }
      if (outputFile.exists()) {
        ZipUtil.unzip(null, myCoursesDir, outputFile, null, null, true);
        if (!outputFile.delete()) {
          LOG.error("Failed to delete", outputFile.getName());
        }
        File[] files = myCoursesDir.listFiles();
        if (files != null) {
          for (File file : files) {
            String fileName = file.getName();
            if (StudyUtils.isZip(fileName)) {
              ZipUtil.unzip(null, new File(myCoursesDir, fileName.substring(0, fileName.indexOf("."))), file, null, null, true);
              if (!file.delete()) {
                LOG.error("Failed to delete", fileName);
              }
            }
          }
        }
      } else {
        LOG.debug("failed to download course");
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (GeneratorException e) {
      LOG.error(e);
    }
  }

  public Map<CourseInfo, File> getLoadedCourses() {
    return myCourses;
  }

  /**
   * Parses courses located in {@link com.jetbrains.python.edu.StudyDirectoryProjectGenerator#myCoursesDir}
   * to {@link com.jetbrains.python.edu.StudyDirectoryProjectGenerator#myCourses}
   *
   * @return map with course names and course files location
   */
  public Map<CourseInfo, File> loadCourses() {
    Map<CourseInfo, File> courses = new HashMap<CourseInfo, File>();
    if (myCoursesDir.exists()) {
      File[] courseDirs = myCoursesDir.listFiles(new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          return pathname.isDirectory();
        }
      });
      for (File courseDir : courseDirs) {
        addCourse(courses, courseDir);
      }
    }
    return courses;
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
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(courseFile)));
        JsonReader r = new JsonReader(reader);
        JsonParser parser = new JsonParser();
        JsonElement el = parser.parse(r);
        String courseName = el.getAsJsonObject().get(COURSE_NAME_ATTRIBUTE).getAsString();
        String courseAuthor = el.getAsJsonObject().get(AUTHOR_ATTRIBUTE).getAsString();
        String courseDescription = el.getAsJsonObject().get("description").getAsString();
        courseInfo = new CourseInfo(courseName, courseAuthor, courseDescription);
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

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    return myValidationResult;
  }

  public void setValidationResult(ValidationResult validationResult) {
    myValidationResult = validationResult;
  }

  /**
   * @return courses from memory or from cash file or parses course directory
   */
  public Map<CourseInfo, File> getCourses() {
    if (!myCourses.isEmpty()) {
      return myCourses;
    }
    if (myCoursesDir.exists()) {
      File cacheFile = new File(myCoursesDir, CACHE_NAME);
      if (cacheFile.exists()) {
        myCourses = getCoursesFromCache(cacheFile);
        if (!myCourses.isEmpty()) {
          return myCourses;
        }
      }
      myCourses = loadCourses();
      if (!myCourses.isEmpty()) {
        return myCourses;
      }
    }
    downloadAndUnzip(false);
    myCourses = loadCourses();
    flushCache();
    return myCourses;
  }

  /**
   * Writes courses to cash file {@link com.jetbrains.python.edu.StudyDirectoryProjectGenerator#CACHE_NAME}
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
      for (Map.Entry<CourseInfo, File> course : myCourses.entrySet()) {
        CourseInfo courseInfo = course.getKey();
        String line = String
          .format("name=%s path=%s author=%s description=%s", courseInfo.getName(), course.getValue(), courseInfo.getAuthor(),
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
   * Loads courses from {@link com.jetbrains.python.edu.StudyDirectoryProjectGenerator#CACHE_NAME} file
   *
   * @return map of course names and course files
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static Map<CourseInfo, File> getCoursesFromCache(File cashFile) {
    Map<CourseInfo, File> coursesFromCash = new HashMap<CourseInfo, File>();
    BufferedReader reader =  null;
    try {
       reader = new BufferedReader(new InputStreamReader(new FileInputStream(cashFile)));
      String line;

      while ((line = reader.readLine()) != null) {
        Matcher matcher = CACHE_PATTERN.matcher(line);
        if (matcher.matches()) {
          String courseName = matcher.group(2);
          File file = new File(matcher.group(4));
          String author = matcher.group(6);
          String description = matcher.group(8);
          CourseInfo courseInfo = new CourseInfo(courseName, author, description);
          if (file.exists()) {
            coursesFromCash.put(courseInfo, file);
          }
        }
      }
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      StudyUtils.closeSilently(reader);
    }
    return coursesFromCash;
  }

  @Nullable
  @Override
  public JPanel extendBasePanel() throws ProcessCanceledException {
    StudyNewProjectPanel settingsPanel = new StudyNewProjectPanel(this);
    settingsPanel.registerValidators(new FacetValidatorsManager() {
      public void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch) {
        throw new UnsupportedOperationException();
      }
      public void validate() {
        fireStateChanged();
      }
    });
    return settingsPanel.getContentPanel();
  }
}
