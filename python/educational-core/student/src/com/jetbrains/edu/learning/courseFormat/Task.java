package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of task which contains task files, tests, input file for tests
 */
public class Task implements StudyItem {
  @Expose private String name;

  // index is visible to user number of task from 1 to task number
  private int myIndex;
  private StudyStatus myStatus = StudyStatus.Unchecked;

  @SerializedName("stepic_id")
  @Expose private int myStepicId;
  
  @SerializedName("task_files")
  @Expose public Map<String, TaskFile> taskFiles = new HashMap<>();

  private String text;
  private Map<String, String> testsText = new HashMap<>();

  @Transient private Lesson myLesson;
  @Expose @SerializedName("update_date") private Date myUpdateDate;

  public Task() {}

  public Task(@NotNull final String name) {
    this.name = name;
  }

  /**
   * Initializes state of task file
   *
   * @param lesson lesson which task belongs to
   */
  public void initTask(final Lesson lesson, boolean isRestarted) {
    setLesson(lesson);
    if (!isRestarted) myStatus = StudyStatus.Unchecked;
    for (TaskFile taskFile : getTaskFiles().values()) {
      taskFile.initTaskFile(this, isRestarted);
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getText() {
    return text;
  }

  public void setText(final String text) {
    this.text = text;
  }

  public int getIndex() {
    return myIndex;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public Map<String, String> getTestsText() {
    return testsText;
  }

  public void addTestsTexts(String name, String text) {
    testsText.put(name, text);
  }

  public Map<String, TaskFile> getTaskFiles() {
    return taskFiles;
  }

  @Nullable
  public TaskFile getTaskFile(final String name) {
    return name != null ? taskFiles.get(name) : null;
  }

  public boolean isTaskFile(@NotNull final String fileName) {
    return taskFiles.get(fileName) != null;
  }

  public void addTaskFile(@NotNull final String name, int index) {
    TaskFile taskFile = new TaskFile();
    taskFile.setIndex(index);
    taskFile.setTask(this);
    taskFile.name = name;
    taskFiles.put(name, taskFile);
  }

  public void addTaskFile(@NotNull final TaskFile taskFile) {
    taskFiles.put(taskFile.name, taskFile);
  }

  @Nullable
  public TaskFile getFile(@NotNull final String fileName) {
    return taskFiles.get(fileName);
  }

  @Transient
  public Lesson getLesson() {
    return myLesson;
  }

  @Transient
  public void setLesson(Lesson lesson) {
    myLesson = lesson;
  }

  @Nullable
  public VirtualFile getTaskDir(@NotNull final Project project) {
    String lessonDirName = EduNames.LESSON + String.valueOf(myLesson.getIndex());
    String taskDirName = EduNames.TASK + String.valueOf(myIndex);
    VirtualFile courseDir = project.getBaseDir();
    if (courseDir != null) {
      VirtualFile lessonDir = courseDir.findChild(lessonDirName);
      if (lessonDir != null) {
        return lessonDir.findChild(taskDirName);
      }
    }
    return null;
  }

  @NotNull
  public String getTaskText(@NotNull final Project project) {
    if (!StringUtil.isEmptyOrSpaces(text)) return text;
    final VirtualFile taskDir = getTaskDir(project);
    if (taskDir != null) {
      final VirtualFile file = StudyUtils.findTaskDescriptionVirtualFile(taskDir);
      if (file == null) return "";
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        return document.getImmutableCharSequence().toString();
      }
    }

    return "";
  }

  @NotNull
  public String getTestsText(@NotNull final Project project) {
    final VirtualFile taskDir = getTaskDir(project);
    if (taskDir != null) {
      final VirtualFile file = taskDir.findChild(EduNames.TESTS_FILE);
      if (file == null) return "";
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        return document.getImmutableCharSequence().toString();
      }
    }

    return "";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Task task = (Task)o;

    if (myIndex != task.myIndex) return false;
    if (name != null ? !name.equals(task.name) : task.name != null) return false;
    if (taskFiles != null ? !taskFiles.equals(task.taskFiles) : task.taskFiles != null) return false;
    if (text != null ? !text.equals(task.text) : task.text != null) return false;
    if (testsText != null ? !testsText.equals(task.testsText) : task.testsText != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + myIndex;
    result = 31 * result + (taskFiles != null ? taskFiles.hashCode() : 0);
    result = 31 * result + (text != null ? text.hashCode() : 0);
    result = 31 * result + (testsText != null ? testsText.hashCode() : 0);
    return result;
  }

  public void setStepicId(int stepicId) {
    myStepicId = stepicId;
  }

  public int getStepicId() {
    return myStepicId;
  }

  public StudyStatus getStatus() {
    return myStatus;
  }
  
  public void setStatus(StudyStatus status) {
    myStatus = status;
    for (TaskFile taskFile : taskFiles.values()) {
      for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
        placeholder.setStatus(status);
      }
    }
  }

  public Task copy() {
    Element element = XmlSerializer.serialize(this);
    Task copy = XmlSerializer.deserialize(element, Task.class);
    if (copy == null) {
      return null;
    }
    copy.initTask(null, true);
    return copy;
  }

  public void setUpdateDate(Date date) {
    myUpdateDate = date;
  }

  public Date getUpdateDate() {
    return myUpdateDate;
  }

  public boolean isUpToDate() {
    if (getStepicId() == 0) return true;
    final Date date = EduStepicConnector.getTaskUpdateDate(getStepicId());
    if (date == null) return true;
    if (myUpdateDate == null) return false;
    return !date.after(myUpdateDate);
  }
}
