package com.jetbrains.edu;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;

public class EduUtils {
  private EduUtils() {
  }
  private static final Logger LOG = Logger.getInstance(EduUtils.class.getName());


  public static void markDirAsSourceRoot(@NotNull final VirtualFile dir, @NotNull final Project project) {
    final Module module = ModuleUtilCore.findModuleForFile(dir, project);
    if (module == null) {
      LOG.info("Module for " + dir.getPath() + " was not found");
      return;
    }
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final ContentEntry entry = MarkRootActionBase.findContentEntry(model, dir);
    if (entry == null) {
      LOG.info("Content entry for " + dir.getPath() + " was not found");
      return;
    }
    entry.addSourceFolder(dir, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
        module.getProject().save();
      }
    });
  }

  public static void enableAction(@NotNull final AnActionEvent event, boolean isEnable) {
    final Presentation presentation = event.getPresentation();
    presentation.setVisible(isEnable);
    presentation.setEnabled(isEnable);
  }

  /**
   * Gets number index in directory names like "task1", "lesson2"
   *
   * @param fullName    full name of directory
   * @param logicalName part of name without index
   * @return index of object
   */
  public static int getIndex(@NotNull final String fullName, @NotNull final String logicalName) {
    if (!fullName.contains(logicalName)) {
      return -1;
    }
    return Integer.parseInt(fullName.substring(logicalName.length())) - 1;
  }

  public static boolean indexIsValid(int index, Collection collection) {
    int size = collection.size();
    return index >= 0 && index < size;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static VirtualFile flushWindows(@NotNull final TaskFile taskFile, @NotNull final VirtualFile file,
                                         boolean useLength) {
    final VirtualFile taskDir = file.getParent();
    VirtualFile fileWindows = null;
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      LOG.debug("Couldn't flush windows");
      return null;
    }
    if (taskDir != null) {
      final String name = file.getNameWithoutExtension() + "_windows";
      PrintWriter printWriter = null;
      try {
        fileWindows = taskDir.createChildData(taskFile, name);
        printWriter = new PrintWriter(new FileOutputStream(fileWindows.getPath()));
        for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
          if (!answerPlaceholder.isValid(document)) {
            printWriter.println("#educational_plugin_window = ");
            continue;
          }
          int start = answerPlaceholder.getRealStartOffset(document);
          int length = useLength ? answerPlaceholder.getLength() : answerPlaceholder.getPossibleAnswerLength();
          final String windowDescription = document.getText(new TextRange(start, start + length));
          printWriter.println("#educational_plugin_window = " + windowDescription);
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        if (printWriter != null) {
          printWriter.close();
        }
        synchronize();
      }
    }
    return fileWindows;
  }

  public static void synchronize() {
    FileDocumentManager.getInstance().saveAllDocuments();
    SaveAndSyncHandler.getInstance().refreshOpenFiles();
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
  }

  public static void createStudentFileFromAnswer(@NotNull final Project project,
                                                 @NotNull final VirtualFile userFileDir,
                                                 @NotNull final VirtualFile answerFileDir,
                                                 @NotNull final Map.Entry<String, TaskFile> taskFileEntry) {
    final String name = taskFileEntry.getKey();
    final TaskFile taskFile = taskFileEntry.getValue();
    VirtualFile file = userFileDir.findChild(name);
    if (file != null) {
      try {
        file.delete(project);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    try {
      userFileDir.createChildData(project, name);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    file = userFileDir.findChild(name);
    assert file != null;
    String answerFileName = file.getNameWithoutExtension() + ".answer." + file.getExtension();
    VirtualFile answerFile = answerFileDir.findChild(answerFileName);
    if (answerFile == null) {
      return;
    }
    final Document answerDocument = FileDocumentManager.getInstance().getDocument(answerFile);
    if (answerDocument == null) {
      return;
    }
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return;

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(0, document.getTextLength(), answerDocument.getCharsSequence());
          }
        });
      }
    }, "Create Student File", "Create Student File");
    EduDocumentListener listener = new EduDocumentListener(taskFile, false);
    document.addDocumentListener(listener);
    taskFile.sortAnswerPlaceholders();
    for (int i = taskFile.getAnswerPlaceholders().size() - 1; i >= 0; i--) {
      final AnswerPlaceholder answerPlaceholder = taskFile.getAnswerPlaceholders().get(i);
      replaceAnswerPlaceholder(project, document, answerPlaceholder);
    }
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    }, "Create Student File", "Create Student File");
    document.removeDocumentListener(listener);
  }

  private static void replaceAnswerPlaceholder(@NotNull final Project project,
                                               @NotNull final Document document,
                                               @NotNull final AnswerPlaceholder answerPlaceholder) {
    final String taskText = answerPlaceholder.getTaskText();
    final int offset = answerPlaceholder.getRealStartOffset(document);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(offset, offset + answerPlaceholder.getPossibleAnswerLength(), taskText);
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    }, "Replace Answer Placeholders", "Replace Answer Placeholders");
  }
}
