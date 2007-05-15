package com.intellij.openapi.vcs.changes.committed;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;
import java.text.DateFormat;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author yole
 */
public class ChangeListDetailsAction extends AnAction {
  @NonNls private static Pattern ourHtmlPattern = Pattern.compile("(http:|https:)\\/\\/([^\\s()](?!&(gt|lt|nbsp)+;))+[^\\p{Pe}\\p{Pc}\\p{Pd}\\p{Ps}\\p{Po}\\s]/?");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final ChangeList[] changeLists = e.getData(DataKeys.CHANGE_LISTS);
    if (changeLists != null && changeLists.length > 0 && changeLists [0] instanceof CommittedChangeList) {
      showDetailsPopup(project, (CommittedChangeList) changeLists [0]);
    }
  }

  public void update(final AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final ChangeList[] changeLists = e.getData(DataKeys.CHANGE_LISTS);
    e.getPresentation().setEnabled(project != null && changeLists != null && changeLists.length > 0 &&
      changeLists [0] instanceof CommittedChangeList);
  }

  public static void showDetailsPopup(final Project project, final CommittedChangeList changeList) {
    StringBuilder detailsBuilder = new StringBuilder("<html><body>");
    DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
    final AbstractVcs vcs = changeList.getVcs();
    if (vcs != null) {
      CachingCommittedChangesProvider provider = vcs.getCachingCommittedChangesProvider();
      if (provider != null && provider.getChangelistTitle() != null) {
        detailsBuilder.append(provider.getChangelistTitle()).append(" #").append(changeList.getNumber()).append("<br>");
      }
    }
    detailsBuilder.append("Committed by <b>").append(changeList.getCommitterName()).append("</b> at ");
    detailsBuilder.append(dateFormat.format(changeList.getCommitDate())).append("<br>");
    String comment = XmlStringUtil.escapeString(changeList.getComment());
    comment = ourHtmlPattern.matcher(comment).replaceAll("<a href=\"$0\">$0</a>");

    IssueNavigationConfiguration config = IssueNavigationConfiguration.getInstance(project);
    for(IssueNavigationLink link: config.getLinks()) {
      Pattern pattern = Pattern.compile(link.getIssueRegexp());
      comment = pattern.matcher(comment).replaceAll("<a href=\"" + link.getLinkRegexp() + "\">$0</a>");
    }

    detailsBuilder.append(comment.replace("\n", "<br>"));
    detailsBuilder.append("</body></html>");

    JEditorPane editorPane = new JEditorPane(UIUtil.HTML_MIME, detailsBuilder.toString());
    editorPane.setEditable(false);
    editorPane.setBackground(HintUtil.INFORMATION_COLOR);
    editorPane.select(0, 0);
    editorPane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          BrowserUtil.launchBrowser(e.getDescription());
        }
      }
    });
    JScrollPane scrollPane = new JScrollPane(editorPane);
    final JBPopup hint =
      JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, editorPane)
        .setDimensionServiceKey(project, "changelist.details.popup", false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(true)
        .setTitle(VcsBundle.message("changelist.details.title"))
        .createPopup();
    hint.showInBestPositionFor(DataManager.getInstance().getDataContext());
  }
}