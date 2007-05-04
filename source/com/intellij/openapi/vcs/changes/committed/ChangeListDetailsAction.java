package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.ide.DataManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;

import javax.swing.*;
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
    detailsBuilder.append("Changelist #").append(changeList.getNumber());
    detailsBuilder.append("<br>Committed by <b>").append(changeList.getCommitterName()).append("</b> at ");
    detailsBuilder.append(dateFormat.format(changeList.getCommitDate())).append("<br>");
    detailsBuilder.append(XmlStringUtil.escapeString(changeList.getComment()));
    detailsBuilder.append("</body></html>");

    JEditorPane editorPane = new JEditorPane(UIUtil.HTML_MIME, detailsBuilder.toString());
    editorPane.setEditable(false);
    editorPane.setBackground(HintUtil.INFORMATION_COLOR);
    JScrollPane scrollPane = new JScrollPane(editorPane);
    final JBPopup hint =
      JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, editorPane)
        .setDimensionServiceKey(project, JavaDocManager.JAVADOC_LOCATION_AND_SIZE, false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(true)
        .setTitle(VcsBundle.message("changelist.details.title"))
        .createPopup();
    hint.showInBestPositionFor(DataManager.getInstance().getDataContext());
  }
}