package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens changes in study files and updates
 * coordinates of all the windows in current task file
 */
public abstract class CCDocumentListener extends DocumentAdapter {
  private final TaskFile myTaskFile;
  private final List<TaskWindowWrapper> myTaskWindows = new ArrayList<TaskWindowWrapper>();


  public CCDocumentListener(TaskFile taskFile) {
    myTaskFile = taskFile;
  }


  //remembering old end before document change because of problems
  // with fragments containing "\n"
  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    Document document = e.getDocument();
    myTaskWindows.clear();
    for (AnswerPlaceholder answerPlaceholder : myTaskFile.getAnswerPlaceholders()) {
      int twStart = answerPlaceholder.getRealStartOffset(document);
      int length = useLength() ? answerPlaceholder.getLength() : answerPlaceholder.getPossibleAnswerLength();
      int twEnd = twStart + length;
      myTaskWindows.add(new TaskWindowWrapper(answerPlaceholder, twStart, twEnd));
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
        AnswerPlaceholder answerPlaceholder = taskWindowWrapper.getAnswerPlaceholder();
        int line = document.getLineNumber(twStart);
        int start = twStart - document.getLineStartOffset(line);
        int length = twEnd - twStart;
        answerPlaceholder.setLine(line);
        answerPlaceholder.setStart(start);
        if (useLength()) {
          answerPlaceholder.setLength(length);
        } else {
          answerPlaceholder.setPossibleAnswer(document.getText(TextRange.create(start, start + length )));
        }
      }
    }
  }

  protected abstract  boolean useLength();

  private static class TaskWindowWrapper {
    public AnswerPlaceholder myAnswerPlaceholder;
    public int myTwStart;
    public int myTwEnd;

    public TaskWindowWrapper(AnswerPlaceholder answerPlaceholder, int twStart, int twEnd) {
      myAnswerPlaceholder = answerPlaceholder;
      myTwStart = twStart;
      myTwEnd = twEnd;
    }

    public int getTwStart() {
      return myTwStart;
    }

    public int getTwEnd() {
      return myTwEnd;
    }

    public AnswerPlaceholder getAnswerPlaceholder() {
      return myAnswerPlaceholder;
    }
  }
}

