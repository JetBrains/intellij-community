package com.jetbrains.python.edu;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.course.TaskWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens changes in study files and updates
 * coordinates of all the windows in current task file
 */
public class StudyDocumentListener extends DocumentAdapter {
  private final TaskFile myTaskFile;
  private List<TaskWindowWrapper> myTaskWindows = new ArrayList<TaskWindowWrapper>();

  public StudyDocumentListener(TaskFile taskFile) {
    myTaskFile = taskFile;
  }


  //remembering old end before document change because of problems
  // with fragments containing "\n"
  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    Document document = e.getDocument();
    myTaskWindows.clear();
    for (TaskWindow taskWindow : myTaskFile.getTaskWindows()) {
      int twStart = taskWindow.getRealStartOffset(document);
      int twEnd = twStart + taskWindow.getLength();
      myTaskWindows.add(new TaskWindowWrapper(taskWindow, twStart, twEnd));
    }
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    if (e instanceof DocumentEventImpl) {
      DocumentEventImpl event = (DocumentEventImpl)e;
      Document document = e.getDocument();
      int offset = e.getOffset();
      int change = event.getNewLength() - event.getOldLength();
      for (TaskWindowWrapper taskWindowWrapper : myTaskWindows) {
        int twStart = taskWindowWrapper.getTwStart();
        if (twStart > offset) {
          twStart += change;
        }
        int twEnd = taskWindowWrapper.getTwEnd();
        if (twEnd >= offset) {
          twEnd += change;
        }
        TaskWindow taskWindow = taskWindowWrapper.getTaskWindow();
        int line = document.getLineNumber(twStart);
        int start = twStart - document.getLineStartOffset(line);
        int length = twEnd - twStart;
        taskWindow.setLine(line);
        taskWindow.setStart(start);
        taskWindow.setLength(length);
      }
    }
  }

  private static class TaskWindowWrapper {
    public TaskWindow myTaskWindow;
    public int myTwStart;
    public int myTwEnd;

    public TaskWindowWrapper(TaskWindow taskWindow, int twStart, int twEnd) {
      myTaskWindow = taskWindow;
      myTwStart = twStart;
      myTwEnd = twEnd;
    }

    public int getTwStart() {
      return myTwStart;
    }

    public int getTwEnd() {
      return myTwEnd;
    }

    public TaskWindow getTaskWindow() {
      return myTaskWindow;
    }
  }
}
