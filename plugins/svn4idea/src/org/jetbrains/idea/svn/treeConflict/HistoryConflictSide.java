// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.treeConflict;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.ui.JBColor;
import gnu.trove.TLongArrayList;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.conflict.ConflictVersion;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;

import javax.swing.*;
import java.util.List;

import static com.intellij.vcsUtil.VcsUtil.getFilePathOnNonLocal;

final class HistoryConflictSide implements ConflictSidePresentation {
  public static final int LIMIT = 10;

  private final VcsAppendableHistoryPartnerAdapter mySessionAdapter;
  private final SvnHistoryProvider myProvider;
  private final FilePath myPath;
  private final SvnVcs myVcs;
  private final ConflictVersion myVersion;
  private final Revision myPeg;
  private FileHistoryPanelImpl myFileHistoryPanel;
  private TLongArrayList myListToReportLoaded;

  HistoryConflictSide(SvnVcs vcs, ConflictVersion version, final Revision peg) throws VcsException {
    myVcs = vcs;
    myVersion = version;
    myPeg = peg;
    myPath =
      getFilePathOnNonLocal(version.getRepositoryRoot().appendPath(version.getPath(), false).toDecodedString(), version.isDirectory());

    mySessionAdapter = new VcsAppendableHistoryPartnerAdapter();
    myProvider = myVcs.getVcsHistoryProvider();
  }

  public void setListToReportLoaded(TLongArrayList listToReportLoaded) {
    myListToReportLoaded = listToReportLoaded;
  }

  @Override
  public void load() throws VcsException {
    Revision from = Revision.of(myVersion.getPegRevision());
    myProvider.reportAppendableHistory(myPath, mySessionAdapter, from, myPeg, myPeg == null ? LIMIT : 0, myPeg, true);
    VcsAbstractHistorySession session = mySessionAdapter.getSession();
    if (myListToReportLoaded != null && session != null) {
      List<VcsFileRevision> list = session.getRevisionList();
      for (VcsFileRevision revision : list) {
        myListToReportLoaded.add(((SvnRevisionNumber)revision.getRevisionNumber()).getRevision().getNumber());
      }
    }
  }

  @Override
  public void dispose() {
    if (myFileHistoryPanel != null) {
      Disposer.dispose(myFileHistoryPanel);
    }
  }

  @Override
  public JPanel createPanel() {
    VcsAbstractHistorySession session = mySessionAdapter.getSession();
    if (session == null) return EmptyConflictSide.INSTANCE.createPanel();
    List<VcsFileRevision> list = session.getRevisionList();
    if (list.isEmpty()) {
      return EmptyConflictSide.INSTANCE.createPanel();
    }
    VcsFileRevision last = null;
    if (myPeg == null && list.size() == LIMIT ||
        myPeg != null && myPeg.getNumber() > 0 &&
        myPeg.equals(((SvnRevisionNumber)list.get(list.size() - 1).getRevisionNumber()).getRevision())) {
      last = list.remove(list.size() - 1);
    }
    myFileHistoryPanel = new FileHistoryPanelImpl(myVcs, myPath, session, myProvider, new FileHistoryRefresherI() {
      @Override
      public void refresh(boolean canUseCache) {
        //we will not refresh
      }

      @Override
      public void selectContent() {
      }

      @Override
      public boolean isInRefresh() {
        return false;
      }
    }, true);
    myFileHistoryPanel.setBottomRevisionForShowDiff(last);
    myFileHistoryPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    return myFileHistoryPanel;
  }
}
