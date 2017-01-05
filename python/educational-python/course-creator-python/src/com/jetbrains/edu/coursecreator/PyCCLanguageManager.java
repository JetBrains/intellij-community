package com.jetbrains.edu.coursecreator;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class PyCCLanguageManager implements CCLanguageManager {
  private static final Logger LOG = Logger.getInstance(PyCCLanguageManager.class);

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
    return getInternalTemplateByName(project, "task.py");
  }

  @Nullable
  @Override
  public FileTemplate getTestsTemplate(@NotNull final Project project) {
    return getInternalTemplateByName(project, EduNames.TESTS_FILE);
  }

  @Override
  public boolean doNotPackFile(File pathname) {
    String name = pathname.getName();
    return name.contains("__pycache__") || name.contains(".pyc");
  }

  private static FileTemplate getInternalTemplateByName(@NotNull final Project project, String name) {
    return FileTemplateManager.getInstance(project).getInternalTemplate(name);
  }

  @Override
  public boolean isTestFile(VirtualFile file) {
    String name = file.getName();
    if (EduNames.TESTS_FILE.equals(name)) {
      return true;
    }
    return name.contains(FileUtil.getNameWithoutExtension(EduNames.TESTS_FILE)) && name.contains(EduNames.SUBTASK_MARKER);
  }

  @Override
  public void createTestsForNewSubtask(@NotNull Project project, @NotNull Task task) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    int nextSubtaskIndex = task.getLastSubtaskIndex() + 1;
    String nextSubtaskTestsFileName = getSubtaskTestsFileName(nextSubtaskIndex);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        PsiDirectory taskPsiDir = PsiManager.getInstance(project).findDirectory(taskDir);
        FileTemplate testsTemplate = getTestsTemplate(project);
        if (taskPsiDir == null || testsTemplate == null) {
          return;
        }
        FileTemplateUtil.createFromTemplate(testsTemplate, nextSubtaskTestsFileName, null, taskPsiDir);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }

  @NotNull
  public static String getSubtaskTestsFileName(int index) {
    return index == 0 ? EduNames.TESTS_FILE : FileUtil.getNameWithoutExtension(EduNames.TESTS_FILE) +
                                              EduNames.SUBTASK_MARKER +
                                              index + "." +
                                              FileUtilRt.getExtension(EduNames.TESTS_FILE);
  }
}