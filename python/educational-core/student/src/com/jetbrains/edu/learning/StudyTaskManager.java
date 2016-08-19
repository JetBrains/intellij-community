package com.jetbrains.edu.learning;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.stepic.StepicUser;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
  private static final Logger LOG = Logger.getInstance(StudyTaskManager.class);
  public static final int CURRENT_VERSION = 3;
  private StepicUser myUser = new StepicUser();
  private Course myCourse;
  public int VERSION = 3;

  public Map<Task, List<UserTest>> myUserTests = new HashMap<>();
  public List<String> myInvisibleFiles = new ArrayList<>();

  public boolean myShouldUseJavaFx = StudyUtils.hasJavaFx();
  private StudyToolWindow.StudyToolWindowMode myToolWindowMode = StudyToolWindow.StudyToolWindowMode.TEXT;
  private boolean myTurnEditingMode = false;
  private boolean myEnableTestingFromSamples = true;

  @Transient private final Project myProject;

  public StudyTaskManager(Project project) {
    myProject = project;
  }

  public StudyTaskManager() {
    this(null);
  }

  public void setCourse(Course course) {
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
    return userTests != null ? userTests : Collections.emptyList();
  }

  public void removeUserTest(@NotNull final Task task, @NotNull final UserTest userTest) {
    final List<UserTest> userTests = myUserTests.get(task);
    if (userTests != null) {
      userTests.remove(userTest);
    }
  }

  public JBColor getColor(@NotNull final AnswerPlaceholder placeholder) {
    final StudyStatus status = placeholder.getStatus();
    if (status == StudyStatus.Solved) {
      return JBColor.GREEN;
    }
    if (status == StudyStatus.Failed) {
      return JBColor.RED;
    }
    return JBColor.BLUE;
  }

  public boolean hasFailedAnswerPlaceholders(@NotNull final TaskFile taskFile) {
    return taskFile.getAnswerPlaceholders().size() > 0 && taskFile.hasFailedPlaceholders();
  }

  @Nullable
  @Override
  public Element getState() {
    if (myCourse == null) {
      return null;
    }
    Element el = new Element("taskManager");
    Element courseElement = new Element(StudySerializationUtils.Xml.MAIN_ELEMENT);
    XmlSerializer.serializeInto(this, courseElement);
    el.addContent(courseElement);
    return el;
  }

  @Override
  public void loadState(Element state) {
    try {
      int version = StudySerializationUtils.Xml.getVersion(state);
      if (version == -1) {
        LOG.error("StudyTaskManager doesn't contain any version:\n" + state.getValue());
        return;
      }
      switch (version) {
        case 1:
          state = StudySerializationUtils.Xml.convertToSecondVersion(state);
        case 2:
          state = StudySerializationUtils.Xml.convertToThirdVersion(state, myProject);
          //uncomment for future versions
          //case 3:
          //state = StudySerializationUtils.Xml.convertToForthVersion(state, myProject);
      }
      XmlSerializer.deserializeInto(this, state.getChild(StudySerializationUtils.Xml.MAIN_ELEMENT));
      VERSION = CURRENT_VERSION;
      if (myCourse != null) {
        myCourse.initCourse(true);
        if (version != VERSION) {
          final File updatedCourse = new File(StudyProjectGenerator.OUR_COURSES_DIR, myCourse.getName());
          if (updatedCourse.exists()) {
            myCourse.setCourseDirectory(updatedCourse.getAbsolutePath());
          }
        }
      }
    }
    catch (StudySerializationUtils.StudyUnrecognizedFormatException e) {
      LOG.error("Unexpected course format:\n", new XMLOutputter().outputString(state));
    }
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

  public boolean shouldUseJavaFx() {
    return myShouldUseJavaFx;
  }

  public void setShouldUseJavaFx(boolean shouldUseJavaFx) {
    this.myShouldUseJavaFx = shouldUseJavaFx;
  }

  public StudyToolWindow.StudyToolWindowMode getToolWindowMode() {
    return myToolWindowMode;
  }

  public void setToolWindowMode(StudyToolWindow.StudyToolWindowMode toolWindowMode) {
    myToolWindowMode = toolWindowMode;
  }

  public boolean isTurnEditingMode() {
    return myTurnEditingMode;
  }

  public void setTurnEditingMode(boolean turnEditingMode) {
    myTurnEditingMode = turnEditingMode;
  }
  
  @NotNull
  public StepicUser getUser() {
    return myUser;
  }

  public void setUser(@NotNull final StepicUser user) {
    myUser = user;
  }

  public boolean isEnableTestingFromSamples() {
    return myEnableTestingFromSamples;
  }

  public void setEnableTestingFromSamples(boolean enableTestingFromSamples) {
    myEnableTestingFromSamples = enableTestingFromSamples;
  }
}
