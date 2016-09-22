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
    return name.contains(FileUtil.getNameWithoutExtension(EduNames.TESTS_FILE)) && name.contains(EduNames.SUBTASK_MARKER);
  }

  @Override
  public void createTestsForNewSubtask(@NotNull Project project, @NotNull Task task) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }

    int prevSubtaskIndex = task.getSubtaskNum() - 1;
    String name = prevSubtaskIndex == 0 ? EduNames.TESTS_FILE : getSubtaskTestsFileName(prevSubtaskIndex);
    VirtualFile testsFile = taskDir.findChild(name);
    if (testsFile == null) {
      return;
    }
    Document document = FileDocumentManager.getInstance().getDocument(testsFile);
    if (document == null) {
      return;
    }
    CharSequence prevTestText = document.getCharsSequence();
    int nextSubtaskIndex = prevSubtaskIndex + 1;
    String nextSubtaskTestsFileName = getSubtaskTestsFileName(nextSubtaskIndex);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        VirtualFile nextSubtaskTestsFile = taskDir.createChildData(this, nextSubtaskTestsFileName);
        StudyTaskManager.getInstance(project).addInvisibleFiles(nextSubtaskTestsFile.getPath());
        Document nextSubtaskDocument = FileDocumentManager.getInstance().getDocument(nextSubtaskTestsFile);
        if (nextSubtaskDocument == null) {
          return;
        }
        String header = "# This is test for subtask " + nextSubtaskIndex + ". We've already copied tests from previous subtask here.\n\n";
        DocumentUtil.writeInRunUndoTransparentAction(() -> {
          nextSubtaskDocument.insertString(0, header);
          nextSubtaskDocument.insertString(header.length(), prevTestText);
          FileDocumentManager.getInstance().saveDocument(nextSubtaskDocument);
        });
      }
      catch (IOException e) {
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