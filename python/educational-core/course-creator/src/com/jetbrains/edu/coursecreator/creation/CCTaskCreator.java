package com.jetbrains.edu.coursecreator.creation;

import com.intellij.ide.IdeView;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CCTaskCreator {
  LanguageExtension<CCTaskCreator> INSTANCE = new LanguageExtension<>("Edu.CCTaskCreator");

  PsiDirectory createTask(@NotNull final Project project, @NotNull final StudyItem item,
                             @Nullable final IdeView view, @NotNull final PsiDirectory parentDirectory,
                             @NotNull final Course course);
}
