package com.jetbrains.edu.coursecreator;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface CCLanguageManager {
  LanguageExtension<CCLanguageManager> INSTANCE = new LanguageExtension<>("Edu.CCLanguageManager");

  boolean doNotPackFile(File pathname);

  default boolean isTestFile(VirtualFile file) {
    return false;
  }

  default void createTestsForNewSubtask(@NotNull Project project, @NotNull Task task) {}
}
