package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;

/**
 * author: lesya
 */
public class ShowNextChangeMarkerAction extends ShowChangeMarkerAction {

  public ShowNextChangeMarkerAction(final Range range, final LineStatusTracker lineStatusTracker, final Editor editor) {
    super(range, lineStatusTracker, editor);
  }

  public ShowNextChangeMarkerAction() {
  }

  protected Range extractRange(LineStatusTracker lineStatusTracker, int line, Editor editor) {
    return lineStatusTracker.getNextRange(line);
  }

}
