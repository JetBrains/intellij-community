package com.intellij.openapi.localVcs.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;

import java.util.Iterator;
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
    LineStatusTracker tracker = ProjectLevelVcsManagerEx.getInstanceEx(myProject).getLineStatusTracker(myDocument);
    if (tracker == null) {
      tracker = ProjectLevelVcsManagerEx.getInstanceEx(myProject).setUpToDateContent(myDocument, myUpToDateContent);
    }
    return calcLineNumber(tracker, currentNumber);
  }


  private static int calcLineNumber(LineStatusTracker tracker, int currentNumber){
    List ranges = tracker.getRanges();
    int result = currentNumber;

    for (Iterator each = ranges.iterator(); each.hasNext();) {
      Range range = (Range) each.next();
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


