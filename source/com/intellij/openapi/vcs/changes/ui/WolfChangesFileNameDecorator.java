package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.SimpleTextAttributes;

import java.awt.*;

/**
 * @author yole
 */
public class WolfChangesFileNameDecorator extends ChangesFileNameDecorator {
  private WolfTheProblemSolver myProblemSolver;

  public WolfChangesFileNameDecorator(final WolfTheProblemSolver problemSolver) {
    myProblemSolver = problemSolver;
  }

  public void appendFileName(final ChangesBrowserNodeRenderer renderer, final VirtualFile vFile, final String fileName, final Color color, final boolean highlightProblems) {
    int style = SimpleTextAttributes.STYLE_PLAIN;
    Color underlineColor = null;
    if (highlightProblems && vFile != null && !vFile.isDirectory() && myProblemSolver.isProblemFile(vFile)) {
      underlineColor = Color.red;
      style = SimpleTextAttributes.STYLE_WAVED;
    }
    renderer.append(fileName, new SimpleTextAttributes(style, color, underlineColor));
  }
}