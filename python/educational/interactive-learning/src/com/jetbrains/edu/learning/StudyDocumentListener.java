package com.jetbrains.edu.learning;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens changes in study files and updates
 * coordinates of all the windows in current task file
 */
public class StudyDocumentListener extends DocumentAdapter {
  private final TaskFile myTaskFile;
  private List<AnswerPlaceholderWrapper> myAnswerPlaceholders = new ArrayList<AnswerPlaceholderWrapper>();

  public StudyDocumentListener(@NotNull final TaskFile taskFile) {
    myTaskFile = taskFile;
  }


  //remembering old end before document change because of problems
  // with fragments containing "\n"
  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    if (!myTaskFile.isTrackChanges()) {
      return;
    }
    myTaskFile.setHighlightErrors(true);
    final Document document = e.getDocument();
    myAnswerPlaceholders.clear();
    for (AnswerPlaceholder answerPlaceholder : myTaskFile.getAnswerPlaceholders()) {
      int twStart = answerPlaceholder.getRealStartOffset(document);
      int twEnd = twStart + answerPlaceholder.getLength();
      myAnswerPlaceholders.add(new AnswerPlaceholderWrapper(answerPlaceholder, twStart, twEnd));
    }
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    if (!myTaskFile.isTrackChanges()) {
      return;
    }
    if (e instanceof DocumentEventImpl) {
      final DocumentEventImpl event = (DocumentEventImpl)e;
      final Document document = e.getDocument();
      int offset = e.getOffset();
      int change = event.getNewLength() - event.getOldLength();
      for (AnswerPlaceholderWrapper answerPlaceholderWrapper : myAnswerPlaceholders) {
        int twStart = answerPlaceholderWrapper.getTwStart();
        if (twStart > offset) {
          twStart += change;
        }
        int twEnd = answerPlaceholderWrapper.getTwEnd();
        if (twEnd >= offset) {
          twEnd += change;
        }
        final AnswerPlaceholder answerPlaceholder = answerPlaceholderWrapper.getAnswerPlaceholder();
        int line = document.getLineNumber(twStart);
        int start = twStart - document.getLineStartOffset(line);
        int length = twEnd - twStart;
        answerPlaceholder.setLine(line);
        answerPlaceholder.setStart(start);
        answerPlaceholder.setLength(length);
      }
    }
  }

  private static class AnswerPlaceholderWrapper {
    public AnswerPlaceholder myAnswerPlaceholder;
    public int myTwStart;
    public int myTwEnd;

    public AnswerPlaceholderWrapper(AnswerPlaceholder answerPlaceholder, int twStart, int twEnd) {
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
