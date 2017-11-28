package com.jetbrains.edu.learning;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.checker.StudyTaskChecker;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.tasks.PyCharmTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.edu.learning.courseGeneration.StudyGenerator;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public interface EduPluginConfigurator {
  String EP_NAME = "Edu.pluginConfigurator";
  LanguageExtension<EduPluginConfigurator> INSTANCE = new LanguageExtension<>(EP_NAME);

  @NotNull
  String getTestFileName();

  /**
   * Default language name used to publish submissions to stepik
   * @return
   */
  @NotNull
  default String getStepikDefaultLanguage() {
    return "";
  }

  /**
   * Creates content (including its directory or module) of new lesson in project
   *
   * @param project Parameter is used in Java and Kotlin plugins
   * @param lesson  Lesson to create content for. It's already properly initialized and added to course.
   * @return PsiDirectory of created lesson
   */
  default PsiDirectory createLessonContent(@NotNull Project project,
                                           @NotNull Lesson lesson,
                                           @Nullable IdeView view,
                                           @NotNull PsiDirectory parentDirectory) {
    final PsiDirectory[] lessonDirectory = new PsiDirectory[1];
    ApplicationManager.getApplication().runWriteAction(() -> {
      String lessonDirName = EduNames.LESSON + lesson.getIndex();
      lessonDirectory[0] = DirectoryUtil.createSubdirectories(lessonDirName, parentDirectory, "\\/");
    });
    if (lessonDirectory[0] != null) {
      if (view != null) {
        view.selectElement(lessonDirectory[0]);
      }
    }
    return lessonDirectory[0];
  }

  /**
   * Creates content (including its directory or module) of new task in project
   *
   * @param task Task to create content for. It's already properly initialized and added to corresponding lesson.
   * @return PsiDirectory of created task
   */
  PsiDirectory createTaskContent(@NotNull final Project project,
                                 @NotNull final Task task,
                                 @Nullable final IdeView view,
                                 @NotNull final PsiDirectory parentDirectory,
                                 @NotNull final Course course);

  /**
   * Used in educator plugin to filter files to be packed into course archive
   */
  boolean excludeFromArchive(@NotNull String name);

  /**
   * @return true for all the test files including tests for subtasks
   */
  default boolean isTestFile(VirtualFile file) {
    return false;
  }

  default void createTestsForNewSubtask(@NotNull Project project, @NotNull TaskWithSubtasks task) {
  }

  /**
   * Used for code highlighting in Task Description tool window
   *
   * @return parameter for CodeMirror script. Available languages: @see <@linktourl http://codemirror.net/mode/>
   */
  @NotNull
  default String getDefaultHighlightingMode() {
    return "";
  }

  /**
   * Used for code highlighting in Task Description tool window
   * Example in <a href="https://github.com/JetBrains/intellij-community/tree/master/python/educational-python/Edu-Python">Edu Python</a> plugin
   */
  @NotNull
  default String getLanguageScriptUrl() {
    return "";
  }

  @NotNull
  StudyTaskChecker<PyCharmTask> getPyCharmTaskChecker(@NotNull PyCharmTask task, @NotNull Project project);

  @NotNull
  default DefaultActionGroup getTaskDescriptionActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    String[] ids = new String[]{
      StudyCheckAction.ACTION_ID,
      StudyPreviousTaskAction.ACTION_ID,
      StudyNextTaskAction.ACTION_ID,
      StudyRefreshTaskFileAction.ACTION_ID,
      StudyShowHintAction.ACTION_ID
    };
    ActionManager actionManager = ActionManager.getInstance();
    Arrays.stream(ids)
      .map(actionManager::getAction)
      .filter(Objects::nonNull)
      .forEach(group::add);

    group.add(new StudyEditInputAction());
    return group;
  }

  /**
   * Configures (adds libraries for example) task module for languages that require modules
   * <br>
   * Example in <a href="https://github.com/JetBrains/educational-plugins/tree/master/Edu-Utils/Edu-Kotlin">Edu Kotlin</a> plugin
   */
  default void configureModule(@NotNull Module module) {
  }

  /**
   * Creates module structure for given course
   * <br>
   * Example in <a href="https://github.com/JetBrains/educational-plugins/tree/master/Edu-Utils/Edu-Kotlin">Edu Kotlin</a> plugin
   */
  default void createCourseModuleContent(@NotNull ModifiableModuleModel moduleModel,
                                         @NotNull Project project,
                                         @NotNull Course course,
                                         @Nullable String moduleDir) {
    StudyGenerator.createCourse(course, project.getBaseDir());
  }

  default List<String> getBundledCoursePaths() {
    return Collections.emptyList();
  }

  default EduCourseProjectGenerator getEduCourseProjectGenerator() {
    return null;
  }

  default ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }
}
