package com.jetbrains.edu.coursecreator;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class PyCCLanguageManager implements CCLanguageManager {
  @Nullable
  @Override
  public String getDefaultTaskFileExtension() {
    return "py";
  }

  @Nullable
  @Override
  public FileTemplate getTaskFileTemplateForExtension(@NotNull final Project project, String extension) {
    if (!extension.equals("py")) {
      return null;
    }
    return getInternalTemplateByName(project, "task.answer.py");
  }

  @Nullable
  @Override
  public FileTemplate getTestsTemplate(@NotNull final Project project) {
    return getInternalTemplateByName(project, "tests.py");
  }

  @Override
  public boolean packFile(File pathname) {
    String name = pathname.getName();
    return !name.contains("__pycache__") && !name.contains(".pyc");
  }

  @Override
  public String[] getAdditionalFilesToPack() {
    return new String[]{"test_helper.py"};
  }

  private static FileTemplate getInternalTemplateByName(@NotNull final Project project, String name) {
    return FileTemplateManager.getInstance(project).getInternalTemplate(name);
  }
}
