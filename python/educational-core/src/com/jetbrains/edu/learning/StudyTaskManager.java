package com.jetbrains.edu.learning;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jetbrains.edu.learning.StudySerializationUtils.Xml.REMOTE_COURSE;

/**
 * Implementation of class which contains all the information
 * about study in context of current project
 */

@State(name = "StudySettings", storages = @Storage("study_project.xml"))
public class StudyTaskManager implements PersistentStateComponent<Element>, DumbAware {
  private static final Logger LOG = Logger.getInstance(StudyTaskManager.class);
  public static final int CURRENT_VERSION = 6;
  private Course myCourse;
  public int VERSION = CURRENT_VERSION;

  public final Map<Task, List<UserTest>> myUserTests = new HashMap<>();

  private StudyToolWindow.StudyToolWindowMode myToolWindowMode = StudyToolWindow.StudyToolWindowMode.TEXT;
  private boolean myTurnEditingMode = false;

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
    if (!placeholder.getUseLength() && placeholder.isActive() && placeholder.getActiveSubtaskInfo().isNeedInsertText()) {
      return JBColor.LIGHT_GRAY;
    }
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
    return taskFile.getActivePlaceholders().size() > 0 && taskFile.hasFailedPlaceholders();
  }

  @Nullable
  @Override
  public Element getState() {
    if (myCourse == null) {
      return null;
    }

    return serialize();
  }

  @NotNull
  private Element serialize() {
    Element el = new Element("taskManager");
    Element taskManagerElement = new Element(StudySerializationUtils.Xml.MAIN_ELEMENT);
    XmlSerializer.serializeInto(this, taskManagerElement);

    if (myCourse instanceof RemoteCourse) {
      try {
        Element course = new Element(REMOTE_COURSE);
        //noinspection RedundantCast
        XmlSerializer.serializeInto((RemoteCourse)myCourse, course);

        final Element xmlCourse = StudySerializationUtils.Xml.getChildWithName(taskManagerElement, StudySerializationUtils.COURSE);
        xmlCourse.removeContent();
        xmlCourse.addContent(course);
      }
      catch (StudySerializationUtils.StudyUnrecognizedFormatException e) {
        LOG.error("Failed to serialize remote course");
      }
    }

    el.addContent(taskManagerElement);
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
        case 3:
          state = StudySerializationUtils.Xml.convertToForthVersion(state);
        case 4:
          state = StudySerializationUtils.Xml.convertToFifthVersion(state);
          updateTestHelper();
        case 5:
          state = StudySerializationUtils.Xml.convertToSixthVersion(state, myProject);
        //uncomment for future versions
        //case 6:
        //  state = StudySerializationUtils.Xml.convertToSixthVersion(state, myProject);
      }
      deserialize(state);
      VERSION = CURRENT_VERSION;
      if (myCourse != null) {
        myCourse.initCourse(true);
      }
    }
    catch (StudySerializationUtils.StudyUnrecognizedFormatException e) {
      LOG.error("Unexpected course format:\n", new XMLOutputter().outputString(state));
    }
  }

  private void updateTestHelper() {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      final VirtualFile baseDir = myProject.getBaseDir();
      if (baseDir == null) {
        return;
      }
      final VirtualFile testHelper = baseDir.findChild(EduNames.TEST_HELPER);
      if (testHelper != null) {
        StudyUtils.deleteFile(testHelper);
      }
      final FileTemplate template =
        FileTemplateManager.getInstance(myProject).getInternalTemplate(FileUtil.getNameWithoutExtension(EduNames.TEST_HELPER));
      try {
        final PsiDirectory projectDir = PsiManager.getInstance(myProject).findDirectory(baseDir);
        if (projectDir != null) {
          FileTemplateUtil.createFromTemplate(template, EduNames.TEST_HELPER, null, projectDir);
        }
      }
      catch (Exception e) {
        LOG.warn("Failed to create new test helper");
      }
    }));
  }

  private void deserialize(Element state) throws StudySerializationUtils.StudyUnrecognizedFormatException {
    final Element taskManagerElement = state.getChild(StudySerializationUtils.Xml.MAIN_ELEMENT);
    if (taskManagerElement == null) {
      throw new StudySerializationUtils.StudyUnrecognizedFormatException();
    }
    XmlSerializer.deserializeInto(this, taskManagerElement);
    final Element xmlCourse = StudySerializationUtils.Xml.getChildWithName(taskManagerElement, StudySerializationUtils.COURSE);
    final Element remoteCourseElement = xmlCourse.getChild(REMOTE_COURSE);
    if (remoteCourseElement != null) {
      final RemoteCourse remoteCourse = new RemoteCourse();
      XmlSerializer.deserializeInto(remoteCourse, remoteCourseElement);
      myCourse = remoteCourse;
    }
  }

  public static StudyTaskManager getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, StudyTaskManager.class);
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

}
