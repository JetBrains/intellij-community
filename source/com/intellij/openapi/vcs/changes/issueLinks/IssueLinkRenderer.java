package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.util.TextRange;

import java.util.List;
import java.awt.*;

/**
 * @author yole
 */
public class IssueLinkRenderer {
  private SimpleColoredComponent myColoredComponent;
  private IssueNavigationConfiguration myIssueNavigationConfiguration;

  private static final SimpleTextAttributes LINK_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, Color.blue);

  public IssueLinkRenderer(final Project project, final SimpleColoredComponent coloredComponent) {
    myIssueNavigationConfiguration = IssueNavigationConfiguration.getInstance(project);
    myColoredComponent = coloredComponent;
  }

  public void appendTextWithLinks(String text) {
    final List<IssueNavigationConfiguration.LinkMatch> list = myIssueNavigationConfiguration.findIssueLinks(text);
    int pos = 0;
    for(IssueNavigationConfiguration.LinkMatch match: list) {
      final TextRange textRange = match.getRange();
      if (textRange.getStartOffset() > pos) {
        myColoredComponent.append(text.substring(pos, textRange.getStartOffset()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      myColoredComponent.append(text.substring(textRange.getStartOffset(), textRange.getEndOffset()), LINK_ATTRIBUTES, match.getTargetUrl());
      pos = textRange.getEndOffset();
    }
    if (pos < text.length()) {
      myColoredComponent.append(text.substring(pos), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}