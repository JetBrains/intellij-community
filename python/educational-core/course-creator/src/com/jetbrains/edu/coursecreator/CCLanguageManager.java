package com.jetbrains.edu.coursecreator;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public interface CCLanguageManager {
  LanguageExtension<CCLanguageManager> INSTANCE = new LanguageExtension<>("Edu.CCLanguageManager");

  @Nullable
  String getDefaultTaskFileExtension();

  @Nullable
  FileTemplate getTaskFileTemplateForExtension(@NotNull final Project project, String extension);

  @Nullable
  FileTemplate getTestsTemplate(@NotNull final Project project);

  boolean doNotPackFile(File pathname);

  default boolean isTestFile(VirtualFile file) {
    return false;
  }
}
