package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.util.TextRange;

import java.util.List;

/**
 * @author yole
 */
public class IssueLinkRenderer {
  private SimpleColoredComponent myColoredComponent;
  private IssueNavigationConfiguration myIssueNavigationConfiguration;

  public IssueLinkRenderer(final Project project, final SimpleColoredComponent coloredComponent) {
    myIssueNavigationConfiguration = IssueNavigationConfiguration.getInstance(project);
    myColoredComponent = coloredComponent;
  }

  public void appendTextWithLinks(String text) {
    appendTextWithLinks(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public void appendTextWithLinks(String text, SimpleTextAttributes baseStyle) {
    final List<IssueNavigationConfiguration.LinkMatch> list = myIssueNavigationConfiguration.findIssueLinks(text);
    int pos = 0;
    for(IssueNavigationConfiguration.LinkMatch match: list) {
      final TextRange textRange = match.getRange();
      if (textRange.getStartOffset() > pos) {
        myColoredComponent.append(text.substring(pos, textRange.getStartOffset()), baseStyle);
      }
      myColoredComponent.append(text.substring(textRange.getStartOffset(), textRange.getEndOffset()), getLinkAttributes(baseStyle), match.getTargetUrl());
      pos = textRange.getEndOffset();
    }
    if (pos < text.length()) {
      myColoredComponent.append(text.substring(pos), baseStyle);
    }
  }

  private static SimpleTextAttributes getLinkAttributes(final SimpleTextAttributes baseStyle) {
    return (baseStyle.getStyle() & SimpleTextAttributes.STYLE_BOLD) != 0 ? SimpleTextAttributes.LINK_BOLD_ATTRIBUTES : SimpleTextAttributes.LINK_ATTRIBUTES;
  }
}