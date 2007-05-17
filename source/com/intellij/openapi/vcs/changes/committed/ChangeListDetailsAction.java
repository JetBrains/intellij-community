package com.intellij.openapi.vcs.changes.committed;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.text.DateFormat;

/**
 * @author yole
 */
public class ChangeListDetailsAction extends AnAction {
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
    CachingCommittedChangesProvider provider = null;
    if (vcs != null) {
      provider = vcs.getCachingCommittedChangesProvider();
      if (provider != null && provider.getChangelistTitle() != null) {
        detailsBuilder.append(provider.getChangelistTitle()).append(" #").append(changeList.getNumber()).append("<br>");
      }
    }
    detailsBuilder.append("Committed by <b>").append(changeList.getCommitterName()).append("</b> at ");
    detailsBuilder.append(dateFormat.format(changeList.getCommitDate())).append("<br>");

    if (provider != null) {
      for(ChangeListColumn column: provider.getColumns()) {
        if (ChangeListColumn.isCustom(column)) {
          String value = column.getValue(changeList).toString();
          if (value.length() == 0) {
            value = "<none>";
          }
          detailsBuilder.append(column.getTitle()).append(": ").append(XmlStringUtil.escapeString(value)).append("<br>");
        }
      }
    }

    detailsBuilder.append(IssueLinkHtmlRenderer.formatTextWithLinks(project, changeList.getComment()));
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