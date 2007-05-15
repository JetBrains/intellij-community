package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.util.TextRange;
import com.intellij.xml.util.XmlStringUtil;

import java.util.List;

/**
 * @author yole
 */
public class IssueLinkHtmlRenderer {
  public static String formatTextWithLinks(final Project project, final String c) {
    String comment = XmlStringUtil.escapeString(c);

    StringBuilder commentBuilder = new StringBuilder();
    IssueNavigationConfiguration config = IssueNavigationConfiguration.getInstance(project);
    final List<IssueNavigationConfiguration.LinkMatch> list = config.findIssueLinks(comment);
    int pos = 0;
    for(IssueNavigationConfiguration.LinkMatch match: list) {
      TextRange range = match.getRange();
      commentBuilder.append(comment.substring(pos, range.getStartOffset())).append("<a href=\"").append(match.getTargetUrl()).append("\">");
      commentBuilder.append(comment.substring(range.getStartOffset(), range.getEndOffset())).append("</a>");
      pos = range.getEndOffset();
    }
    commentBuilder.append(comment.substring(pos));
    comment = commentBuilder.toString();

    final String str = comment.replace("\n", "<br>");
    return str;
  }
}