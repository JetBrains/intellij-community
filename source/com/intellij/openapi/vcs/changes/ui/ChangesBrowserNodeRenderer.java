package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class ChangesBrowserNodeRenderer extends ColoredTreeCellRenderer {
  private final boolean myShowFlatten;
  private WolfTheProblemSolver myProblemSolver;
  private IssueLinkRenderer myIssueLinkRenderer;
  private final boolean myHighlightProblems;

  public ChangesBrowserNodeRenderer(final Project project, final boolean showFlatten, final boolean highlightProblems) {
    myShowFlatten = showFlatten;
    myProblemSolver = WolfTheProblemSolver.getInstance(project);
    myHighlightProblems = highlightProblems;
    myIssueLinkRenderer = new IssueLinkRenderer(project, this);
  }

  public boolean isShowFlatten() {
    return myShowFlatten;
  }

  public void customizeCellRenderer(JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    ChangesBrowserNode node = (ChangesBrowserNode)value;
    node.render(this, selected, expanded, hasFocus);
  }


  protected void appendFileName(final VirtualFile vFile, final String fileName, final Color color) {
    int style = SimpleTextAttributes.STYLE_PLAIN;
    Color underlineColor = null;
    if (myHighlightProblems && vFile != null && !vFile.isDirectory() && myProblemSolver.isProblemFile(vFile)) {
      underlineColor = Color.red;
      style = SimpleTextAttributes.STYLE_WAVED;
    }
    append(fileName, new SimpleTextAttributes(style, color, underlineColor));
  }

  public void appendTextWithIssueLinks(final String text, final SimpleTextAttributes baseStyle) {
    myIssueLinkRenderer.appendTextWithLinks(text, baseStyle);
  }
}
