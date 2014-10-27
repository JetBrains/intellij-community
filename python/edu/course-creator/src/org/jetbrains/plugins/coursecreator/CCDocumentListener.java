package org.jetbrains.plugins.coursecreator;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import org.jetbrains.plugins.coursecreator.format.TaskFile;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens changes in study files and updates
 * coordinates of all the windows in current task file
 */
public abstract class CCDocumentListener extends DocumentAdapter {
  private final TaskFile myTaskFile;
  private List<TaskWindowWrapper> myTaskWindows = new ArrayList<TaskWindowWrapper>();


  public CCDocumentListener(TaskFile taskFile) {
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
      int length = useLength() ? taskWindow.getLength() : taskWindow.getReplacementLength();
      int twEnd = twStart + length;
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
        if (useLength()) {
          taskWindow.setLength(length);
        } else {
          taskWindow.setReplacementLength(length);
        }
      }
    }
  }

  protected abstract  boolean useLength();

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

