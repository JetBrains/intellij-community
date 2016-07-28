package com.jetbrains.edu.coursecreator;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

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
    return name.contains(FileUtil.getNameWithoutExtension(EduNames.TESTS_FILE)) && name.contains(EduNames.STEP_MARKER);
  }

  @Override
  public void createTestsForNewStep(@NotNull Project project, @NotNull Task task) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }

    int prevStepIndex = task.getActiveStepIndex();
    String name = prevStepIndex == -1 ? EduNames.TESTS_FILE : getStepTestsFileName(prevStepIndex);
    VirtualFile testsFile = taskDir.findChild(name);
    if (testsFile == null) {
      return;
    }
    Document document = FileDocumentManager.getInstance().getDocument(testsFile);
    if (document == null) {
      return;
    }
    CharSequence prevTestText = document.getCharsSequence();
    String nextStepTestsFileName = getStepTestsFileName(prevStepIndex + 1);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        VirtualFile nextStepTestsFile = taskDir.createChildData(this, nextStepTestsFileName);
        StudyTaskManager.getInstance(project).addInvisibleFiles(nextStepTestsFile.getPath());
        Document nextStepDocument = FileDocumentManager.getInstance().getDocument(nextStepTestsFile);
        if (nextStepDocument == null) {
          return;
        }
        int index = prevStepIndex + 2;
        //TODO: text for header
        String header = "# This is test for step " + index + ". We've already copied tests from previous step here.\n\n";
        DocumentUtil.writeInRunUndoTransparentAction(() -> {
          nextStepDocument.insertString(0, header);
          nextStepDocument.insertString(header.length(), prevTestText);
          FileDocumentManager.getInstance().saveDocument(nextStepDocument);
        });
      }
      catch (IOException e) {
        LOG.error(e);
      }
    });
  }

    @NotNull
    public static String getStepTestsFileName(int index) {
      if (index == -1) {
        return EduNames.TESTS_FILE;
      }
      return FileUtil.getNameWithoutExtension(EduNames.TESTS_FILE) +
             EduNames.STEP_MARKER +
             index + "." +
             FileUtilRt.getExtension(EduNames.TESTS_FILE);
    }
}
