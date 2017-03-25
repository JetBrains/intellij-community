package com.jetbrains.edu.learning.courseGeneration;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.zip.JBZipEntry;
import com.intellij.util.io.zip.JBZipFile;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.CourseInfo;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.edu.learning.StudyUtils.execCancelable;

public class StudyProjectGenerator {
  private static final Logger LOG = Logger.getInstance(StudyProjectGenerator.class.getName());
  private final List<SettingsListener> myListeners = ContainerUtil.newArrayList();
  private List<CourseInfo> myCourses = new ArrayList<>();
  private List<Course> myLocalCourses = new ArrayList<>();
  private List<Integer> myEnrolledCoursesIds = new ArrayList<>();
  protected CourseInfo mySelectedCourseInfo;

  public void setCourses(List<CourseInfo> courses) {
    myCourses = courses;
  }

  public boolean isLoggedIn() {
    final StepicUser user = StudySettings.getInstance().getUser();
    return user != null;
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
    final Course course = getCourse(project);
    if (course == null) {
      LOG.warn("Course is null");
      Messages.showWarningDialog("Some problems occurred while creating the course", "Error in Course Creation");
      return;
    }
    else if (course.isAdaptive() && !StudyUtils.isCourseValid(course)) {
      Messages.showWarningDialog("There is no recommended tasks for this adaptive course", "Error in Course Creation");
      return;
    }
    StudyTaskManager.getInstance(project).setCourse(course);
    ApplicationManager.getApplication().runWriteAction(() -> {
      StudyGenerator.createCourse(course, baseDir);
      StudyUtils.registerStudyToolWindow(course, project);
      StudyUtils.openFirstTask(course, project);
      EduUsagesCollector.projectTypeCreated(course.isAdaptive() ? EduNames.ADAPTIVE : EduNames.STUDY);
    });
  }

  @Nullable
  public Course getCourse(@NotNull final Project project) {
    final CourseInfo info = mySelectedCourseInfo;
    for (Course course : myLocalCourses) {
      if (course.getName().equals(info.getName())){
        course.initCourse(false);
        return course;
      }

    }
    return getCourseFromStepic(project);
  }

  private Course getCourseFromStepic(@NotNull Project project) {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
      return execCancelable(() -> {
        final Course course = EduStepicConnector.getCourse(project, mySelectedCourseInfo);
        if (StudyUtils.isCourseValid(course)) {
          course.initCourse(false);
        }
        return course;
      });
    }, "Creating Course", true, project);
  }

  // Supposed to be called under progress
  public List<CourseInfo> getCourses(boolean force) {
    if (force) {
      myCourses = execCancelable(() -> EduStepicConnector.getCourses(StepicUpdateSettings.getInstance().getUser()));
    }
    if (myCourses == null || myCourses.isEmpty() || (myCourses.size() == 1 && myCourses.contains(CourseInfo.INVALID_COURSE))) {
      myCourses = Collections.singletonList(getBundledIntro());
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

  @Nullable
  public CourseInfo getBundledIntro() {
    final LanguageExtensionPoint<EduPluginConfigurator>[] extensions = Extensions.getExtensions(EduPluginConfigurator.EP_NAME, null);
    for (LanguageExtensionPoint<EduPluginConfigurator> extension : extensions) {
      final EduPluginConfigurator configurator = extension.getInstance();
      final String path = configurator.getBundledCoursePath();
      if (path != null) {
        return getCourseInfo(path);
      }
    }
    return null;
  }

  @Nullable
  public CourseInfo addLocalCourse(String zipFilePath) {
    final CourseInfo courseInfo = getCourseInfo(zipFilePath);
    if (courseInfo != null) {
      myCourses.add(0, courseInfo);
    }
    return courseInfo;
  }

  @Nullable
  public CourseInfo getCourseInfo(String zipFilePath) {
    try {
      final JBZipFile zipFile = new JBZipFile(zipFilePath);
      final JBZipEntry entry = zipFile.getEntry(EduNames.COURSE_META_FILE);
      byte[] bytes = entry.getData();
      final String jsonText = new String(bytes, CharsetToolkit.UTF8_CHARSET);
      Gson gson = new GsonBuilder()
        .registerTypeAdapter(Task.class, new StudySerializationUtils.Json.TaskDeserializer())
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();
      Course course = gson.fromJson(jsonText, Course.class);
      final CourseInfo courseInfo = CourseInfo.fromCourse(course);
      if (courseInfo != null) {
        myLocalCourses.add(course);
      }
      return courseInfo;
    }
    catch (IOException e) {
      LOG.error("Failed to unzip course archive");
      LOG.error(e);
    }
    return null;
  }
}