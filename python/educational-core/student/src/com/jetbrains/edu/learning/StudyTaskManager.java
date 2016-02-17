package com.jetbrains.edu.learning;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.xmlb.XmlSerializer;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.UserTest;
import com.jetbrains.edu.oldCourseFormat.OldCourse;
import org.jdom.Element;
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

@State(name = "StudySettings", storages = @Storage("study_project.xml"))
public class StudyTaskManager implements PersistentStateComponent<Element>, DumbAware {
  private Course myCourse;
  private OldCourse myOldCourse;
  public int VERSION = 2;
  public Map<AnswerPlaceholder, StudyStatus> myStudyStatusMap = new HashMap<>();
  public Map<TaskFile, StudyStatus> myTaskStatusMap = new HashMap<>();
  public Map<Task, List<UserTest>> myUserTests = new HashMap<>();
  public List<String> myInvisibleFiles = new ArrayList<>();

  private StudyTaskManager() {
  }

  public void setCourse(final Course course) {
    myCourse = course;
  }

  @Nullable
  public Course getCourse() {
    return myCourse;
  }

  public void setStatus(AnswerPlaceholder placeholder, StudyStatus status) {
    placeholder.setStatus(status);
  }

  public void addUserTest(@NotNull final Task task, UserTest userTest) {
    List<UserTest> userTests = myUserTests.get(task);
    if (userTests == null) {
      userTests = new ArrayList<>();
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
    task.setStatus(status);
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
    StudyStatus status = placeholder.getStatus();
    if (status != StudyStatus.Uninitialized) return status;
    
    status = myStudyStatusMap.get(placeholder);
    if (status == null) {
      status = StudyStatus.Unchecked;
    }
    placeholder.setStatus(status);
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
    StudyStatus taskStatus = task.getStatus();
    if (taskStatus != StudyStatus.Uninitialized) return taskStatus;
    
    for (TaskFile taskFile : task.getTaskFiles().values()) {
      StudyStatus taskFileStatus = getStatus(taskFile);
      if (taskFileStatus == StudyStatus.Unchecked) {
        task.setStatus(StudyStatus.Unchecked);
        return StudyStatus.Unchecked;
      }
      if (taskFileStatus == StudyStatus.Failed) {
        task.setStatus(StudyStatus.Failed);
        return StudyStatus.Failed;
      }
    }
    task.setStatus(StudyStatus.Solved);
    return StudyStatus.Solved;
  }

  private StudyStatus getStatus(@NotNull final TaskFile file) {
    if (file.getAnswerPlaceholders().isEmpty()) {
      if (myTaskStatusMap == null) return StudyStatus.Solved;
      return myTaskStatusMap.get(file);

    }
    for (AnswerPlaceholder answerPlaceholder : file.getAnswerPlaceholders()) {
      StudyStatus placeholderStatus = getStatus(answerPlaceholder);
      if (placeholderStatus == StudyStatus.Failed) {
        return StudyStatus.Failed;
      }
      if (placeholderStatus == StudyStatus.Unchecked) {
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
  public Element getState() {
    Element el = new Element("taskManager");
    if (myCourse != null) {
      Element courseElement = new Element(MAIN_ELEMENT);
      XmlSerializer.serializeInto(this, courseElement);
      el.addContent(courseElement);
    }
    return el;
  }

  @Override
  public void loadState(Element state) {
    final Element mainElement = state.getChild(MAIN_ELEMENT);
    if (mainElement != null) {
      final StudyTaskManager taskManager = XmlSerializer.deserialize(mainElement, StudyTaskManager.class);
      if (taskManager != null) {
        myCourse = taskManager.myCourse;
        myUserTests = taskManager.myUserTests;
        myInvisibleFiles = taskManager.myInvisibleFiles;
        myTaskStatusMap = taskManager.myTaskStatusMap;
        myStudyStatusMap = taskManager.myStudyStatusMap;
      }
    }
    final Element oldCourseElement = state.getChild(COURSE_ELEMENT);
    if (oldCourseElement != null) {
      myOldCourse = XmlSerializer.deserialize(oldCourseElement, OldCourse.class);
      if (myOldCourse != null) {
        myCourse = EduUtils.transformOldCourse(myOldCourse, new Function<Pair<AnswerPlaceholder, StudyStatus>, Void>() {
          @Override
          public Void fun(Pair<AnswerPlaceholder, StudyStatus> pair) {
            setStatus(pair.first, pair.second);
            return null;
          }
        });
        myOldCourse = null;
      }
    }
    if (myCourse != null) {
      myCourse.initCourse(true);
    }
  }

  public static final String COURSE_ELEMENT = "courseElement";
  public static final String MAIN_ELEMENT = "StudyTaskManager";

  public OldCourse getOldCourse() {
    return myOldCourse;
  }

  public void setOldCourse(OldCourse oldCourse) {
    myOldCourse = oldCourse;
  }

  public static StudyTaskManager getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, StudyTaskManager.class);
  }

  public void addInvisibleFiles(String filePath) {
    myInvisibleFiles.add(filePath);
  }

  public boolean isInvisibleFile(String path) {
    return myInvisibleFiles.contains(path);
  }
}
