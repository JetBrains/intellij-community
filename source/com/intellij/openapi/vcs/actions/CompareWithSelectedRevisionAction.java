package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ListPopup;
import com.intellij.ui.ListSpeedSearch;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;
import java.util.List;

public class CompareWithSelectedRevisionAction extends AbstractVcsAction{
  public void update(VcsContext e, Presentation presentation) {
    AbstractShowDiffAction.updateDiffAction(presentation, e);
  }

  protected void actionPerformed(VcsContext vcsContext) {
    final DefaultListModel model = new DefaultListModel();

    final VirtualFile file = vcsContext.getSelectedFiles()[0];
    final Project project = vcsContext.getProject();
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    final VcsHistoryProvider vcsHistoryProvider = vcs.getVcsHistoryProvider();

    try {
      final VcsHistorySession session = vcsHistoryProvider.createSessionFor(new FilePathImpl(file));
      final List<VcsFileRevision> revisions = session.getRevisionList();
      for (Iterator<VcsFileRevision> iterator = revisions.iterator(); iterator.hasNext();) {
        model.addElement(iterator.next());
      }

    }
    catch (VcsException e1) {
      e1.printStackTrace();
    }

    final JList list = new JList(model);
    list.setCellRenderer(new VcsRevisionListCellRenderer());
    Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index == -1 || index >= list.getModel().getSize()){
          return;
        }
        VcsFileRevision revision = (VcsFileRevision)list.getSelectedValue();
        AbstractShowDiffAction.showDiff(vcs.getDiffProvider(), revision.getRevisionNumber(),
                                        file, project);
      }
    };

    if (list.getModel().getSize() == 0) {
      list.clearSelection();
    }
    new ListSpeedSearch(list);

    Window window = null;

    Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
    if(focusedComponent!=null){
      if(focusedComponent instanceof Window){
        window=(Window)focusedComponent;
      }else{
        window=SwingUtilities.getWindowAncestor(focusedComponent);
      }
    }
    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    }

    Rectangle r;
    if (window != null) {
      r = window.getBounds();
    }
    else {
      r = WindowManagerEx.getInstanceEx().getScreenBounds();
    }

    ListPopup popup = new ListPopup("File Revisions",list, runnable, project);

    if (model.getSize() > 0) {
      Dimension listPreferredSize = list.getPreferredSize();
      list.setVisibleRowCount(0);
      Dimension viewPreferredSize = new Dimension(listPreferredSize.width, Math.min(listPreferredSize.height, r.height - 20));
      ((JViewport)list.getParent()).setPreferredSize(viewPreferredSize);
    }

    popup.getWindow().pack();
    Dimension popupSize=popup.getSize();
    int x = r.x + r.width/2 - popupSize.width/2;
    int y = r.y + r.height/2 - popupSize.height/2;

    popup.show(x,y);

  }

}
