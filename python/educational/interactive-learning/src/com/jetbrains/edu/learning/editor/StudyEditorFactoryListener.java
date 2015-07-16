package com.jetbrains.edu.learning.editor;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.problems.WolfTheProblemSolver;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import com.jetbrains.edu.learning.ui.StudyToolWindowFactory;
import org.jetbrains.annotations.NotNull;


public class StudyEditorFactoryListener implements EditorFactoryListener {

  @Override
  public void editorCreated(@NotNull final EditorFactoryEvent event) {
    final Editor editor = event.getEditor();

    final Project project = editor.getProject();
    if (project == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              final Document document = editor.getDocument();
              final VirtualFile openedFile = FileDocumentManager.getInstance().getFile(document);
              if (openedFile != null) {
                final TaskFile taskFile = StudyUtils.getTaskFile(project, openedFile);
                if (taskFile != null) {
                  StudyNavigator.navigateToFirstAnswerPlaceholder(editor, taskFile);
                  StudyEditor.addDocumentListener(document, new EduDocumentListener(taskFile));
                  WolfTheProblemSolver.getInstance(project).clearProblems(openedFile);
                  StudyUtils.drawAllWindows(editor, taskFile);

                  final ToolWindow studyToolWindow = ToolWindowManager.getInstance(project).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW);
                  if (studyToolWindow != null) {
                    StudyUtils.updateStudyToolWindow(project);
                    studyToolWindow.show(null);
                  }

                }
              }
            }
          });
        }
      }
    );
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    final Editor editor = event.getEditor();
    final Document document = editor.getDocument();
    StudyEditor.removeListener(document);
    editor.getMarkupModel().removeAllHighlighters();
    editor.getSelectionModel().removeSelection();
  }
}
