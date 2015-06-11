package com.jetbrains.edu.learning;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.UserTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
  private Course myCourse;
  public Map<AnswerPlaceholder, StudyStatus> myStudyStatusMap = new HashMap<AnswerPlaceholder, StudyStatus>();
  public Map<Task, List<UserTest>> myUserTests = new HashMap<Task, List<UserTest>>();

  private StudyTaskManager() {
  }

  public void setCourse(@NotNull final Course course) {
    myCourse = course;
  }

  @Nullable
  public Course getCourse() {
    return myCourse;
  }

  public void setStatus(AnswerPlaceholder placeholder, StudyStatus status) {
    if (myStudyStatusMap == null) {
      myStudyStatusMap = new HashMap<AnswerPlaceholder, StudyStatus>();
    }
    myStudyStatusMap.put(placeholder, status);
  }

  public void addUserTest(@NotNull final Task task, UserTest userTest) {
    List<UserTest> userTests = myUserTests.get(task);
    if (userTests == null) {
      userTests = new ArrayList<UserTest>();
      myUserTests.put(task, userTests);
    }
    userTests.add(userTest);
  }

  public void setUserTests(@NotNull final Task task, @NotNull final List<UserTest> userTests) {
    myUserTests.put(task, userTests);
  }

  @NotNull
  public List<UserTest> getUserTests(@NotNull final Task task) {
    final List<UserTest> userTests = myUserTests.get(task);
    return userTests != null ? userTests : Collections.<UserTest>emptyList();
  }

  public void removeUserTest(@NotNull final Task task, @NotNull final UserTest userTest) {
    final List<UserTest> userTests = myUserTests.get(task);
    if (userTests != null) {
      userTests.remove(userTest);
    }
  }


  public void setStatus(Task task, StudyStatus status) {
    for (TaskFile taskFile : task.getTaskFiles().values()) {
      setStatus(taskFile, status);
    }
  }

  public void setStatus(TaskFile file, StudyStatus status) {
    for (AnswerPlaceholder answerPlaceholder : file.getAnswerPlaceholders()) {
      setStatus(answerPlaceholder, status);
    }
  }

  public StudyStatus getStatus(AnswerPlaceholder placeholder) {
    StudyStatus status = myStudyStatusMap.get(placeholder);
    if (status == null) {
      status = StudyStatus.Unchecked;
      myStudyStatusMap.put(placeholder, status);
    }
    return status;
  }


  public StudyStatus getStatus(@NotNull final Lesson lesson) {
    for (Task task : lesson.getTaskList()) {
      StudyStatus taskStatus = getStatus(task);
      if (taskStatus == StudyStatus.Unchecked || taskStatus == StudyStatus.Failed) {
        return StudyStatus.Unchecked;
      }
    }
    return StudyStatus.Solved;
  }

  public StudyStatus getStatus(@NotNull final Task task) {
    for (TaskFile taskFile : task.getTaskFiles().values()) {
      StudyStatus taskFileStatus = getStatus(taskFile);
      if (taskFileStatus == StudyStatus.Unchecked) {
        return StudyStatus.Unchecked;
      }
      if (taskFileStatus == StudyStatus.Failed) {
        return StudyStatus.Failed;
      }
    }
    return StudyStatus.Solved;
  }

  private StudyStatus getStatus(@NotNull final TaskFile file) {
    for (AnswerPlaceholder answerPlaceholder : file.getAnswerPlaceholders()) {
      StudyStatus windowStatus = getStatus(answerPlaceholder);
      if (windowStatus == StudyStatus.Failed) {
        return StudyStatus.Failed;
      }
      if (windowStatus == StudyStatus.Unchecked) {
        return StudyStatus.Unchecked;
      }
    }
    return StudyStatus.Solved;
  }


  public JBColor getColor(@NotNull final AnswerPlaceholder placeholder) {
    final StudyStatus status = getStatus(placeholder);
    if (status == StudyStatus.Solved) {
      return JBColor.GREEN;
    }
    if (status == StudyStatus.Failed) {
      return JBColor.RED;
    }
    return JBColor.BLUE;
  }

  public boolean hasFailedAnswerPlaceholders(@NotNull final TaskFile taskFile) {
    return taskFile.getAnswerPlaceholders().size() > 0 && getStatus(taskFile) == StudyStatus.Failed;
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
      myCourse.initCourse(true);
    }
  }

  public static StudyTaskManager getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, StudyTaskManager.class);
  }
}
