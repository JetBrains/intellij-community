package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.ide.DataManager;

import javax.swing.*;

/**
 * @author yole
 */
public class ChangeListDetailsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final ChangeList[] changeLists = e.getData(DataKeys.CHANGE_LISTS);
    if (changeLists != null && changeLists.length > 0) {
      showDetailsPopup(project, changeLists [0]);
    }
  }

  public static void showDetailsPopup(final Project project, final ChangeList changeList) {
    JEditorPane editorPane = new JEditorPane();
    editorPane.setText(changeList.getComment());
    editorPane.setEditable(false);
    editorPane.setBackground(HintUtil.INFORMATION_COLOR);
    JScrollPane scrollPane = new JScrollPane(editorPane);
    final JBPopup hint =
      JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, editorPane)
        .setDimensionServiceKey(project, JavaDocManager.JAVADOC_LOCATION_AND_SIZE, false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(true)
        .setTitle("Changelist Details")
        .createPopup();
    hint.showInBestPositionFor(DataManager.getInstance().getDataContext());
  }
}