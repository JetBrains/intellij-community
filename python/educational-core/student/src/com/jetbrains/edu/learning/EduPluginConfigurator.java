package com.jetbrains.edu.learning;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface EduPluginConfigurator {
  LanguageExtension<EduPluginConfigurator> INSTANCE = new LanguageExtension<>("Edu.pluginConfigurator");

  @NotNull
  String getTestFileName();

  default PsiDirectory createLesson(@NotNull final StudyItem item,
                                    @Nullable final IdeView view, @NotNull final PsiDirectory parentDirectory) {
    final PsiDirectory[] lessonDirectory = new PsiDirectory[1];
    ApplicationManager.getApplication().runWriteAction(() -> {
      lessonDirectory[0] = DirectoryUtil.createSubdirectories(EduNames.LESSON + item.getIndex(), parentDirectory, "\\/");
    });
    if (lessonDirectory[0] != null) {
      if (view != null) {
        view.selectElement(lessonDirectory[0]);
      }
    }
    return lessonDirectory[0];
  }


  PsiDirectory createTask(@NotNull final Project project, @NotNull final StudyItem item,
                          @Nullable final IdeView view, @NotNull final PsiDirectory parentDirectory,
                          @NotNull final Course course);
}
