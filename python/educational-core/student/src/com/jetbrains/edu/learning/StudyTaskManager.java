package com.jetbrains.edu.learning;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.stepik.StepikUser;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Implementation of class which contains all the information
 * about study in context of current project
 */

@State(name = "StudySettings", storages = @Storage("study_project.xml"))
public class StudyTaskManager implements PersistentStateComponent<Element>, DumbAware {
  private static final Logger LOG = Logger.getInstance(StudyTaskManager.class);
  public static final int CURRENT_VERSION = 3;
  private StepikUser myUser = new StepikUser();
  private Course myCourse;
  public int VERSION = 3;

  public Map<Task, List<UserTest>> myUserTests = new HashMap<>();
  //public Map<Task, LangSetting> langManager = new HashMap<>();
  //public Map<Task, String> currentLang = new HashMap<>();
  //public Map<Task, Set<String>> supportedLang = new HashMap<>();
  private LangManager langManager = new LangManager();
  public List<String> myInvisibleFiles = new ArrayList<>();

  public boolean myShouldUseJavaFx = StudyUtils.hasJavaFx();
  private StudyToolWindow.StudyToolWindowMode myToolWindowMode = StudyToolWindow.StudyToolWindowMode.TEXT;
  private boolean myTurnEditingMode = false;

  private String defaultLang;

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
    if (myCourse == null && myUser.getEmail().isEmpty()) {
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
          String updatedCoursePath = FileUtil.join(PathManager.getConfigPath(), "courses", myCourse.getName());
          if (new File(updatedCoursePath).exists()) {
            myCourse.setCourseDirectory(updatedCoursePath);
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
  public StepikUser getUser() {
    return myUser;
  }

  public void setUser(@NotNull final StepikUser user) {
    myUser = user;
  }

  //public Map<Task, LangSetting> getLangManager() {
  //  return langManager;
  //}
  //
  //public void setLangManager(Map<Task, LangSetting> langManager) {
  //  this.langManager = langManager;
  //}
  //
  //public LangSetting getLang(Task task){
  //  return langManager.get(task);
  //}
  //
  //public void setLang(Task task, LangSetting lang){
  //  langManager.put(task, lang);
  //}

  public void setDefaultLang(String defaultLang) {
    this.defaultLang = defaultLang;
  }

  public String getDefaultLang() {
    return defaultLang;
  }


  //public Map<Task, String> getCurrentLang() {
  //  return currentLang;
  //}
  //
  //public void setCurrentLang(Map<Task, String> currentLang) {
  //  this.currentLang = currentLang;
  //}
  //
  //public Map<Task, Set<String>> getSupportedLang() {
  //  return supportedLang;
  //}
  //
  //public void setSupportedLang(Map<Task, Set<String>> supportedLang) {
  //  this.supportedLang = supportedLang;
  //}
  //
  //public void putCurrentLang(Task task, String lang){
  //  currentLang.put(task, lang);
  //}
  //
  //public String getCurrentLang(Task task) {
  //  return currentLang.get(task);
  //}
  //
  //public void putSupportedLang(Task task, Set<String> supportLangs){
  //  this.supportedLang.put(task, supportLangs);
  //}
  //
  //public Set<String> getSupportedLang(Task task) {
  //  return supportedLang.get(task);
  //}


  public LangManager getLangManager() {
    return langManager;
  }

  public void setLangManager(LangManager langManager) {
    this.langManager = langManager;
  }
}


