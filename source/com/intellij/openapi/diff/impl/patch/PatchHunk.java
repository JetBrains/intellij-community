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
  private int myStartLineAfter;
  private List<PatchLine> myLines = new ArrayList<PatchLine>();

  public PatchHunk(final int lineBefore, final int lineAfter) {
    myStartLineBefore = lineBefore;
    myStartLineAfter = lineAfter;
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
}