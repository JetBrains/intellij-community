package com.jetbrains.edu.learning.core;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens changes in study files and updates
 * coordinates of all the windows in current task file
 */
public class EduDocumentListener extends DocumentAdapter {
  private final TaskFile myTaskFile;
  private final boolean myTrackLength;
  private final List<AnswerPlaceholderWrapper> myAnswerPlaceholders = new ArrayList<>();


  public EduDocumentListener(TaskFile taskFile) {
    myTaskFile = taskFile;
    myTrackLength = true;
  }

  public EduDocumentListener(TaskFile taskFile, boolean trackLength) {
    myTaskFile = taskFile;
    myTrackLength = trackLength;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    if (!myTaskFile.isTrackChanges()) {
      return;
    }
    myTaskFile.setHighlightErrors(true);
    myAnswerPlaceholders.clear();
    for (AnswerPlaceholder answerPlaceholder : myTaskFile.getAnswerPlaceholders()) {
      int twStart = answerPlaceholder.getOffset();
      int length = answerPlaceholder.getRealLength();
      int twEnd = twStart + length;
      myAnswerPlaceholders.add(new AnswerPlaceholderWrapper(answerPlaceholder, twStart, twEnd));
    }
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    if (!myTaskFile.isTrackChanges()) {
      return;
    }
    if (myAnswerPlaceholders.isEmpty()) return;
    if (e instanceof DocumentEventImpl) {
      DocumentEventImpl event = (DocumentEventImpl)e;
      Document document = e.getDocument();
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
        AnswerPlaceholder answerPlaceholder = answerPlaceholderWrapper.getAnswerPlaceholder();
        int length = twEnd - twStart;
        answerPlaceholder.setOffset(twStart);
        if (myTrackLength) {
          if (answerPlaceholder.getUseLength()) {
            answerPlaceholder.setLength(length);
          } else {
            answerPlaceholder.setPossibleAnswer(document.getText(TextRange.create(twStart, twStart + length)));
          }
        }
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

