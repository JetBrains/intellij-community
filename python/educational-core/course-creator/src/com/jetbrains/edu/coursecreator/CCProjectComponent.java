package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.learning.StudyProjectComponent;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class CCProjectComponent extends AbstractProjectComponent {
  private final CCVirtualFileListener myTaskFileLifeListener = new CCVirtualFileListener();
  private final Project myProject;

  protected CCProjectComponent(Project project) {
    super(project);
    myProject = project;
  }

  public void initComponent() {
    VirtualFileManager.getInstance().addVirtualFileListener(myTaskFileLifeListener);
  }

  public void migrateIfNeeded() {
    Course studyCourse = StudyTaskManager.getInstance(myProject).getCourse();
    Course course = CCProjectService.getInstance(myProject).getCourse();
    if (studyCourse == null && course != null) {
      course.setCourseMode(CCUtils.COURSE_MODE);
      File coursesDir = new File(PathManager.getConfigPath(), "courses");
      File courseDir = new File(coursesDir, course.getName() + "-" + myProject.getName());
      course.setCourseDirectory(courseDir.getPath());
      StudyTaskManager.getInstance(myProject).setCourse(course);
      StudyProjectComponent.getInstance(myProject).registerStudyToolWindow(course);
    }
  }

  @NotNull
  public String getComponentName() {
    return "CCProjectComponent";
  }

  public void projectOpened() {
    migrateIfNeeded();
    VirtualFileManager.getInstance().addVirtualFileListener(myTaskFileLifeListener);
  }

  public void projectClosed() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myTaskFileLifeListener);
  }
}
