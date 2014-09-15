package com.jetbrains.python.edu;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.course.TaskWindow;
import com.jetbrains.python.edu.editor.StudyEditor;

/**
 * author: liana
 * data: 7/16/14.
 * Listens changes in study files and updates
 * coordinates of all the windows in current task file
 */
public class StudyDocumentListener extends DocumentAdapter {
  private final TaskFile myTaskFile;
  private final Project myProject;
  private int myOldLine;
  private int myOldLineStartOffset;
  private TaskWindow myTaskWindow;
  private boolean myEmptyDocument;

  public StudyDocumentListener(TaskFile taskFile, Project project) {
    myTaskFile = taskFile;
    myProject = project;
  }


  //remembering old end before document change because of problems
  // with fragments containing "\n"
  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    int offset = e.getOffset();
    int oldEnd = offset + e.getOldLength();
    Document document = e.getDocument();
    myOldLine = document.getLineNumber(oldEnd);
    myEmptyDocument = document.getTextLength() == 0;
    myOldLineStartOffset = document.getLineStartOffset(myOldLine);
    int line = document.getLineNumber(offset);
    int offsetInLine = offset - document.getLineStartOffset(line);
    LogicalPosition pos = new LogicalPosition(line, offsetInLine);
    myTaskWindow = myTaskFile.getTaskWindow(document, pos);

  }

  @Override
  public void documentChanged(DocumentEvent e) {
    if (e instanceof DocumentEventImpl) {
      DocumentEventImpl event = (DocumentEventImpl)e;
      Document document = e.getDocument();
      if (myEmptyDocument) {
          Editor editor = StudyEditor.getSelectedEditor(myProject);
          if (editor != null && editor.getDocument() == document) {
          myTaskFile.drawAllWindows(editor);
          return;
        }
      }
      int offset = e.getOffset();
      int change = event.getNewLength() - event.getOldLength();
      if (myTaskWindow != null) {
        int newLength = myTaskWindow.getLength() + change;
        myTaskWindow.setLength(newLength <= 0 ? 0 : newLength);
        if (e.getNewFragment().equals("\n")) {
          myTaskWindow.setLength(myTaskWindow.getLength() + 1);
        }
      }
      int newEnd = offset + event.getNewLength();
      int newLine = document.getLineNumber(newEnd);
      int lineChange = newLine - myOldLine;
      myTaskFile.incrementLines(myOldLine + 1, lineChange);
      int newEndOffsetInLine = offset + e.getNewLength() - document.getLineStartOffset(newLine);
      int oldEndOffsetInLine = offset + e.getOldLength() - myOldLineStartOffset;
      myTaskFile.updateLine(lineChange, myOldLine, newEndOffsetInLine, oldEndOffsetInLine);
    }
  }
}
