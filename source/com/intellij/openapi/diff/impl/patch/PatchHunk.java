/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 20:20:15
 */
package com.intellij.openapi.diff.impl.patch;

import java.util.ArrayList;
import java.util.List;

public class PatchHunk {
  private int myStartLineBefore;
  private int myEndLineBefore;
  private int myStartLineAfter;
  private int myEndLineAfter;
  private List<PatchLine> myLines = new ArrayList<PatchLine>();

  public PatchHunk(final int startLineBefore, final int endLineBefore, final int startLineAfter, final int endLineAfter) {
    myStartLineBefore = startLineBefore;
    myEndLineBefore = endLineBefore;
    myStartLineAfter = startLineAfter;
    myEndLineAfter = endLineAfter;
  }

  public void addLine(final PatchLine line) {
    myLines.add(line);
  }

  public void apply(final List<String> lines) throws ApplyPatchException {
    int curLine = myStartLineBefore-1;
    for(PatchLine line: myLines) {
      switch (line.getType()) {
        case CONTEXT:
          if (!line.getText().equals(lines.get(curLine))) {
            throw new ApplyPatchException("Context mismatch");
          }
          curLine++;
          break;

        case ADD:
          lines.add(curLine, line.getText());
          curLine++;
          break;

        case REMOVE:
          if (!line.getText().equals(lines.get(curLine))) {
            throw new ApplyPatchException("Context mismatch");
          }
          lines.remove(curLine);
          break;
      }
    }
  }

  public boolean isNewContent() {
    return myStartLineBefore == 0 && myEndLineBefore == 0;
  }

  public boolean isDeletedContent() {
    return myStartLineAfter == 0 && myEndLineAfter == 0;
  }

  public String getText() {
    StringBuilder builder = new StringBuilder();
    for(PatchLine line: myLines) {
      builder.append(line.getText()).append("\n");
    }
    return builder.toString();
  }
}