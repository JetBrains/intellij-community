package com.jetbrains.edu.learning;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public interface EduPluginConfigurator {
  String EP_NAME = "Edu.pluginConfigurator";
  LanguageExtension<EduPluginConfigurator> INSTANCE = new LanguageExtension<>(EP_NAME);

  @NotNull
  String getTestFileName();

  /**
   * Creates content (including its directory or module) of new lesson in project
   * @param project Parameter is used in Java and Kotlin plugins
   * @param lesson Lesson to create content for. It's already properly initialized and added to course.
   * @return PsiDirectory of created lesson
   */
  default PsiDirectory createLessonContent(@NotNull Project project,
                                           @NotNull Lesson lesson,
                                           @Nullable IdeView view,
                                           @NotNull PsiDirectory parentDirectory) {
    final PsiDirectory[] lessonDirectory = new PsiDirectory[1];
    ApplicationManager.getApplication().runWriteAction(() -> {
      lessonDirectory[0] = DirectoryUtil.createSubdirectories(EduNames.LESSON + lesson.getIndex(), parentDirectory, "\\/");
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
   * @param task Task to create content for. It's already properly initialized and added to corresponding lesson.
   * @return PsiDirectory of created task
   */
  PsiDirectory createTaskContent(@NotNull final Project project, @NotNull final Task task,
                                 @Nullable final IdeView view, @NotNull final PsiDirectory parentDirectory,
                                 @NotNull final Course course);

  boolean excludeFromArchive(@NotNull File pathname);

  default boolean isTestFile(VirtualFile file) {
    return false;
  }

  default void createTestsForNewSubtask(@NotNull Project project, @NotNull Task task) {}

  /**
   * @return parameter for CodeMirror script. Available languages: @see <@linktourl http://codemirror.net/mode/>
   */
  @NotNull
  default String getDefaultHighlightingMode() {return "";}

  @NotNull
  default String getLanguageScriptUrl() {return "";}

  StudyCheckAction getCheckAction();

  @NotNull
  default DefaultActionGroup getTaskDescriptionActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(getCheckAction());
    String[] ids = new String[]{StudyPreviousTaskAction.ACTION_ID, StudyNextTaskAction.ACTION_ID, StudyRefreshTaskFileAction.ACTION_ID,
      StudyShowHintAction.ACTION_ID};
    for (String id : ids) {
      AnAction action = ActionManager.getInstance().getAction(id);
      if (action == null) {
        continue;
      }
      group.add(action);
    }
    group.add(new StudyRunAction());
    group.add(new StudyEditInputAction());
    return group;
  }

  default void configureModule(@NotNull Module module) {
  }

  default void createCourseModuleContent(@NotNull ModifiableModuleModel moduleModel,
                                         @NotNull Project project,
                                         @NotNull Course course,
                                         @Nullable String moduleDir) {
  }

}
