package com.jetbrains.edu.learning.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduDocumentListener;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of StudyEditor which has panel with special buttons and task text
 * also @see {@link StudyFileEditorProvider}
 */
public class StudyEditor extends PsiAwareTextEditorImpl {
  private final TaskFile myTaskFile;
  private static final Map<Document, EduDocumentListener> myDocumentListeners = new HashMap<>();

  public StudyEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    super(project, file, TextEditorProvider.getInstance());
    myTaskFile = StudyUtils.getTaskFile(project, file);
  }

  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  public static void addDocumentListener(@NotNull final Document document, @NotNull final EduDocumentListener listener) {
    document.addDocumentListener(listener);
    myDocumentListeners.put(document, listener);
  }

  public static void removeListener(Document document) {
    final EduDocumentListener listener = myDocumentListeners.get(document);
    if (listener != null) {
      document.removeDocumentListener(listener);
    }
    myDocumentListeners.remove(document);
  }
}
