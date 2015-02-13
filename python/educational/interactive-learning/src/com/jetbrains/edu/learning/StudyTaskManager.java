package com.jetbrains.edu.learning;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of class which contains all the information
 * about study in context of current project
 */

@State(
  name = "StudySettings",
  storages = {
    @Storage(
      id = "others",
      file = "$PROJECT_CONFIG_DIR$/study_project.xml",
      scheme = StorageScheme.DIRECTORY_BASED
    )}
)
public class StudyTaskManager implements PersistentStateComponent<StudyTaskManager>, DumbAware {
  public Course myCourse;

  public void setCourse(@NotNull final Course course) {
    myCourse = course;
  }

  private StudyTaskManager() {
  }

  @Nullable
  public Course getCourse() {
    return myCourse;
  }

  @Nullable
  @Override
  public StudyTaskManager getState() {
    return this;
  }

  @Override
  public void loadState(StudyTaskManager state) {
    XmlSerializerUtil.copyBean(state, this);
    if (myCourse != null) {
      StudyGenerator.initCourse(myCourse, true);
    }
  }

  public static StudyTaskManager getInstance(@NotNull final Project project) {
    final Module module = ModuleManager.getInstance(project).getModules()[0];
    return ModuleServiceManager.getService(module, StudyTaskManager.class);
  }

}
