package com.jetbrains.python.edu;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.course.TaskWindow;
import com.jetbrains.python.edu.editor.StudyEditor;

/**
 * Listens changes in study files and updates
 * coordinates of all the windows in current task file
 */
public class StudyDocumentListener extends DocumentAdapter {
  private final TaskFile myTaskFile;
  private final Project myProject;
  private int myOldLine;
  private int myOldLineStartOffset;
  private TaskWindow myTaskWindow;
  private boolean myAffectTaskWindows = false;

  public StudyDocumentListener(TaskFile taskFile, Project project) {
    myTaskFile = taskFile;
    myProject = project;
  }


  //remembering old end before document change because of problems
  // with fragments containing "\n"
  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    if (!myTaskFile.isValid()) {
      return;
    }
    if (e instanceof DocumentEventImpl) {
      DocumentEventImpl event = (DocumentEventImpl)e;
      final Document document = event.getDocument();
      if (event.getNewFragment().equals("")) {
        int start = event.getOffset();
        int end = start + event.getOldLength();
        for (TaskWindow tw : myTaskFile.getTaskWindows()) {
          int twStart = tw.getRealStartOffset(document);
          int twEnd = twStart + tw.getLength();
          if (isAffected(twStart, twEnd, start, end)) {
            PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
            if (psiFile == null) {
              continue;
            }
            VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile == null) {
              continue;
            }
            myAffectTaskWindows = true;
            myTaskFile.setValidAndUpdate(false, virtualFile, myProject);
            Editor editor = StudyEditor.getSelectedEditor(myProject);
            if (editor == null) {
              return;
            }
            editor.getMarkupModel().removeAllHighlighters();
            StudyEditor.addFix(myTaskFile, new InvalidTaskFileFix(event.getOldFragment(), event.getOffset(), myProject));
            return;
          }
        }
      }
    }
    int offset = e.getOffset();
    int oldEnd = offset + e.getOldLength();
    Document document = e.getDocument();
    myOldLine = document.getLineNumber(oldEnd);
    myOldLineStartOffset = document.getLineStartOffset(myOldLine);
    int line = document.getLineNumber(offset);
    int offsetInLine = offset - document.getLineStartOffset(line);
    LogicalPosition pos = new LogicalPosition(line, offsetInLine);
    myTaskWindow = myTaskFile.getTaskWindow(document, pos);
  }

  private static boolean isAffected(int taskWindowStart, int taskWindowEnd, int start, int end) {
    boolean isCovered = taskWindowStart > start && taskWindowEnd < end;
    boolean isIntersectLeft = taskWindowStart > start && taskWindowStart < end && taskWindowEnd >= end;
    boolean isIntersectRight = taskWindowStart <= start && taskWindowEnd > start && taskWindowEnd < end;
    return isCovered || isIntersectLeft || isIntersectRight;
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    if (!myTaskFile.isTrackChanges()) {
      return;
    }
    if (!myTaskFile.isValid() && !myAffectTaskWindows) {
      StudyEditor.deleteFix(myTaskFile);
      EditorNotifications.getInstance(myProject).updateAllNotifications();
      return;
    }
    if (e instanceof DocumentEventImpl) {
      DocumentEventImpl event = (DocumentEventImpl)e;
      Document document = e.getDocument();
      if (myAffectTaskWindows) {
        document.createGuardedBlock(0, document.getTextLength());
        myAffectTaskWindows = false;
        return;
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
