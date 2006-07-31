package com.intellij.openapi.localVcs.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;

import java.util.List;

/**
 * author: lesya
 */
public class UpToDateLineNumberProviderImpl implements UpToDateLineNumberProvider {
  private final Document myDocument;
  private final Project myProject;
  private final String myUpToDateContent;

  public UpToDateLineNumberProviderImpl(Document document, Project project, String upToDateContent) {
    myDocument = document;
    myProject = project;
    myUpToDateContent = upToDateContent;
  }

  public int getLineNumber(int currentNumber) {
    LineStatusTracker tracker = LineStatusTrackerManager.getInstance(myProject).getLineStatusTracker(myDocument);
    if (tracker == null) {
      tracker = LineStatusTrackerManager.getInstance(myProject).setUpToDateContent(myDocument, myUpToDateContent);
    }
    return calcLineNumber(tracker, currentNumber);
  }


  private static int calcLineNumber(LineStatusTracker tracker, int currentNumber){
    if (tracker == null) return -1;
    List ranges = tracker.getRanges();
    int result = currentNumber;

    for (final Object range1 : ranges) {
      Range range = (Range)range1;
      int startOffset = range.getOffset1();
      int endOffset = range.getOffset2();

      if ((startOffset <= currentNumber) && (endOffset > currentNumber)) {
        return ABSENT_LINE_NUMBER;
      }

      if (endOffset > currentNumber) return result;

      int currentRangeLength = endOffset - startOffset;

      result += range.getUpToDateRangeLength() - currentRangeLength;
    }
    return result;

  }

}


