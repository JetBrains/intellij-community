/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.treeConflict;

import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.AbstractRefreshablePanel;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.VcsBackgroundTask;
import gnu.trove.TLongArrayList;
import org.jetbrains.idea.svn.ConflictedSvnChange;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.jetbrains.idea.svn.history.SvnHistorySession;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 4/25/12
 * Time: 5:33 PM
 */
public class TreeConflictRefreshablePanel extends AbstractRefreshablePanel {
  public static final String TITLE = "Resolve tree conflict";
  private final ConflictedSvnChange myChange;
  private final SvnVcs myVcs;
  private SvnRevisionNumber myCommittedRevision;
  private FilePath myPath;
  private final CompositeDisposable myChildDisposables = new CompositeDisposable();
  private final TLongArrayList myRightRevisionsList;

  public TreeConflictRefreshablePanel(Project project, String loadingTitle, BackgroundTaskQueue queue, Change change) {
    super(project, loadingTitle, queue);
    myVcs = SvnVcs.getInstance(project);
    assert change instanceof ConflictedSvnChange;
    myChange = (ConflictedSvnChange) change;
    myPath = ChangesUtil.getFilePath(myChange);
    myRightRevisionsList = new TLongArrayList();
  }

  @Override
  public boolean isStillValid(final Change change) {
    return change.isTreeConflict() && change instanceof ConflictedSvnChange &&
           descriptionsEqual(((ConflictedSvnChange)change).getBeforeDescription(), myChange.getBeforeDescription());
  }

  @Override
  public boolean refreshDataSynch() {
    return true;
  }

  private static boolean descriptionsEqual(SVNTreeConflictDescription d1, SVNTreeConflictDescription d2) {
    if (d1.isPropertyConflict() != d2.isPropertyConflict()) return false;
    if (d1.isTextConflict() != d2.isTextConflict()) return false;
    if (d1.isTreeConflict() != d2.isTreeConflict()) return false;

    if (! d1.getOperation().equals(d2.getOperation())) return false;
    if (! d1.getConflictAction().equals(d2.getConflictAction())) return false;
    if (! Comparing.equal(d1.getConflictReason(), d2.getConflictReason())) return false;
    if (! Comparing.equal(d1.getPath(), d2.getPath())) return false;
    if (! Comparing.equal(d1.getNodeKind(), d2.getNodeKind())) return false;
    if (! compareConflictVersion(d1.getSourceLeftVersion(), d2.getSourceLeftVersion())) return false;
    if (! compareConflictVersion(d1.getSourceRightVersion(), d2.getSourceRightVersion())) return false;
    return true;
  }

  private static boolean compareConflictVersion(SVNConflictVersion v1, SVNConflictVersion v2) {
    if (v1 == null && v2 == null) return true;
    if (v1 == null || v2 == null) return false;
    if (! v1.getKind().equals(v2.getKind())) return false;
    if (! v1.getPath().equals(v2.getPath())) return false;
    if (v1.getPegRevision() != v2.getPegRevision()) return false;
    if (! Comparing.equal(v1.getRepositoryRoot(), v2.getRepositoryRoot())) return false;
    return true;
  }

  @Override
  protected void refreshPresentation() {
  }

  @Override
  protected Object loadImpl() throws VcsException {
    return new BeforeAfter<BeforeAfter<ConflictSidePresentation>>(processDescription(myChange.getBeforeDescription()),
                                                     processDescription(myChange.getAfterDescription()));
  }

  private BeforeAfter<ConflictSidePresentation> processDescription(SVNTreeConflictDescription description) throws VcsException {
    if (description == null) return null;
    if (myChange.getBeforeRevision() != null) {
      myCommittedRevision = (SvnRevisionNumber)SvnHistorySession.getCurrentCommittedRevision(myVcs,
              myChange.getBeforeRevision() != null ? myChange.getBeforeRevision().getFile().getIOFile() : myPath.getIOFile());
    }
    boolean differentURLs = isDifferentURLs(description);

    ConflictSidePresentation leftSide = null;
    ConflictSidePresentation rightSide = null;
    try {
      if (differentURLs) {
        leftSide = createSide(description.getSourceLeftVersion(), null, true);
        rightSide = createSide(description.getSourceRightVersion(), null, false);
        leftSide.load();
        rightSide.load();
      } else {
        //only one side
        leftSide = EmptyConflictSide.getInstance();
        final SVNRevision pegFromLeft;
        if (description.getSourceLeftVersion() == null) {
          pegFromLeft = null;
        }
        else {
          long committed = description.getSourceLeftVersion().getPegRevision();
          if (myCommittedRevision != null && myCommittedRevision.getRevision().getNumber() < committed &&
            myCommittedRevision.getRevision().isValid()) {
            committed = myCommittedRevision.getRevision().getNumber();
          }
          pegFromLeft = SVNRevision.create(committed);
        }
        rightSide = createSide(description.getSourceRightVersion(), pegFromLeft, false);
        rightSide.load();
        return new BeforeAfter<ConflictSidePresentation>(leftSide, rightSide);
      }
    } catch (SVNException e) {
      throw new VcsException(e);
    } finally {
      if (leftSide != null) {
        myChildDisposables.add(leftSide);
      }
      if (rightSide != null) {
        myChildDisposables.add(rightSide);
      }
    }

    return new BeforeAfter<ConflictSidePresentation>(leftSide, rightSide);
  }

  private static boolean isDifferentURLs(SVNTreeConflictDescription description) {
    return description.getSourceLeftVersion() != null && description.getSourceRightVersion() != null &&
                ! Comparing.equal(description.getSourceLeftVersion().getPath(), description.getSourceRightVersion().getPath());
  }

  private ConflictSidePresentation createSide(SVNConflictVersion version, final SVNRevision untilThisOther, final boolean isLeft) throws VcsException {
    if (version == null) return EmptyConflictSide.getInstance();
    if (myChange.getBeforeRevision() != null && myCommittedRevision != null) {
      SvnRevisionNumber number = myCommittedRevision;
      if (isLeft && number.getRevision().isValid() && number.getRevision().getNumber() == version.getPegRevision()) {
        return EmptyConflictSide.getInstance();
      }
    }
    // todo temporally
    /*if (SVNNodeKind.DIR.equals(version.getKind())) {
      return new HistoryAsBrowseChangesConflictSide(myVcs.getProject(), version);
    } else {
      return new HistoryConflictSide(myVcs, version);
    }*/
    HistoryConflictSide side = new HistoryConflictSide(myVcs, version, untilThisOther);
    if (untilThisOther != null && ! isLeft) {
      side.setListToReportLoaded(myRightRevisionsList);
    }
    return side;
  }

  @Override
  protected JPanel dataToPresentation(Object o) {
    final BeforeAfter<BeforeAfter<ConflictSidePresentation>> ba = (BeforeAfter<BeforeAfter<ConflictSidePresentation>>) o;
    final JPanel wrapper = new JPanel(new BorderLayout());
    final JPanel main = new JPanel(new GridBagLayout());

    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                            new Insets(1, 1, 1, 1), 0, 0);
    final String pathComment = myCommittedRevision == null ? "" :
                               " (current: " +
                               myChange.getBeforeRevision().getRevisionNumber().asString() +
                               ", committed: " +
                               myCommittedRevision.asString() +
                               ")";
    final JLabel name = new JLabel(myPath.getName() + pathComment);
    name.setFont(name.getFont().deriveFont(Font.BOLD));
    gb.insets.top = 5;
    main.add(name, gb);
    ++ gb.gridy;
    gb.insets.top = 10;
    appendDescription(myChange.getBeforeDescription(), main, gb, ba.getBefore(), myPath.isDirectory());
    appendDescription(myChange.getAfterDescription(), main, gb, ba.getAfter(), myPath.isDirectory());
    wrapper.add(main, BorderLayout.NORTH);
    return wrapper;
  }

  private void appendDescription(SVNTreeConflictDescription description,
                                 JPanel main,
                                 GridBagConstraints gb,
                                 BeforeAfter<ConflictSidePresentation> ba, boolean directory) {
    if (description == null) return;
    JLabel descriptionLbl = new JLabel(SVNTreeConflictUtil.getHumanReadableConflictDescription(description));
    descriptionLbl.setForeground(Color.red);
    main.add(descriptionLbl, gb);
    ++ gb.gridy;
    //buttons
    gb.insets.top = 0;
    addResolveButtons(description, main, gb);

    addSide(main, gb, ba.getBefore(), description.getSourceLeftVersion(), "Left", directory);
    addSide(main, gb, ba.getAfter(), description.getSourceRightVersion(), "Right", directory);
  }

  private void addResolveButtons(SVNTreeConflictDescription description, JPanel main, GridBagConstraints gb) {
    final FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT, 5, 5);
    JPanel wrapper = new JPanel(flowLayout);
    final JButton both = new JButton("Both");
    final JButton merge = new JButton("Merge");
    final JButton left = new JButton("Accept Yours");
    final JButton right = new JButton("Accept Theirs");
    enableAndSetListener(createBoth(description), both);
    enableAndSetListener(createMerge(description), merge);
    enableAndSetListener(createLeft(description), left);
    enableAndSetListener(createRight(description), right);
    //wrapper.add(both);
    if (merge.isEnabled()) {
      wrapper.add(merge);
    }
    wrapper.add(left);
    wrapper.add(right);
    gb.insets.left = -4;
    main.add(wrapper, gb);
    gb.insets.left = 1;
    ++ gb.gridy;
  }

  private ActionListener createRight(final SVNTreeConflictDescription description) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int ok = Messages.showOkCancelDialog(myVcs.getProject(), "Accept theirs for " + filePath(myPath) + "?",
                                             TITLE, Messages.getQuestionIcon());
        if (Messages.OK != ok) return;
        FileDocumentManager.getInstance().saveAllDocuments();
        final Paths paths = getPaths(description);
        ProgressManager.getInstance().run(
          new VcsBackgroundTask<SVNTreeConflictDescription>(myVcs.getProject(), "Accepting theirs for: " + filePath(paths.myMainPath),
                                                            BackgroundFromStartOption.getInstance(), Collections.singletonList(description),
                                                            true) {
            @Override
            protected void process(SVNTreeConflictDescription d) throws VcsException {
              new SvnTreeConflictResolver(myVcs, paths.myMainPath, myCommittedRevision, paths.myAdditionalPath).resolveSelectTheirsFull(d);
            }

            @Override
            public void onSuccess() {
              super.onSuccess();
              if (executedOk()) {
                VcsBalloonProblemNotifier.showOverChangesView(myProject, "Theirs accepted for " + filePath(paths.myMainPath), MessageType.INFO);
              }
            }
          });
      }
    };
  }

  private Paths getPaths(final SVNTreeConflictDescription description) {
    FilePath mainPath = new FilePathImpl(description.getPath(), SVNNodeKind.DIR.equals(description.getNodeKind()));
    FilePath additionalPath = null;
    if (myChange.isMoved() || myChange.isRenamed()) {
      if (SVNConflictAction.ADD.equals(description.getConflictAction())) {
        mainPath = myChange.getAfterRevision().getFile();
        additionalPath = myChange.getBeforeRevision().getFile();
      } else {
        mainPath = myChange.getBeforeRevision().getFile();
        additionalPath = myChange.getAfterRevision().getFile();
      }
    } else {
      if (myChange.getBeforeRevision() != null) {
        mainPath = myChange.getBeforeRevision().getFile();
      } else {
        mainPath = myChange.getAfterRevision().getFile();
      }
    }
    return new Paths(mainPath, additionalPath);
  }

  private static class Paths {
    public final FilePath myMainPath;
    public final FilePath myAdditionalPath;

    private Paths(FilePath mainPath, FilePath additionalPath) {
      myMainPath = mainPath;
      myAdditionalPath = additionalPath;
    }
  }

  private ActionListener createLeft(final SVNTreeConflictDescription description) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int ok = Messages.showOkCancelDialog(myVcs.getProject(), "Accept yours for " + filePath(myPath) + "?",
                                             TITLE, Messages.getQuestionIcon());
        if (Messages.OK != ok) return;
        FileDocumentManager.getInstance().saveAllDocuments();
        final Paths paths = getPaths(description);
        ProgressManager.getInstance().run(
          new VcsBackgroundTask<SVNTreeConflictDescription>(myVcs.getProject(), "Accepting yours for: " + filePath(paths.myMainPath),
                                                            BackgroundFromStartOption.getInstance(), Collections.singletonList(description),
                                                            true) {
            @Override
            protected void process(SVNTreeConflictDescription d) throws VcsException {
              new SvnTreeConflictResolver(myVcs, paths.myMainPath, myCommittedRevision, paths.myAdditionalPath).resolveSelectMineFull(d);
            }

            @Override
            public void onSuccess() {
              super.onSuccess();
              if (executedOk()) {
                VcsBalloonProblemNotifier.showOverChangesView(myProject, "Yours accepted for " + filePath(paths.myMainPath), MessageType.INFO);
              }
            }
          });
      }
    };
  }

  private ActionListener createMerge(final SVNTreeConflictDescription description) {
    if (isDifferentURLs(description)) {
      return null;
    }
    // my edit, theirs move or delete
    /*if (SVNConflictAction.DELETE.equals(description.getConflictAction()) && description.getSourceLeftVersion() != null) {
      //todo
      return new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          mergeMyEditTheirsDelete();
        }
      };
    }    */
    if (SVNConflictAction.EDIT.equals(description.getConflictAction()) && description.getSourceLeftVersion() != null &&
        SVNConflictReason.DELETED.equals(description.getConflictReason()) && (myChange.isMoved() || myChange.isRenamed()) &&
        myCommittedRevision != null) {
      if (myPath.isDirectory() == SVNNodeKind.DIR.equals(description.getSourceRightVersion().getKind())) {
        return createMergeTheirsForFile(description);
      }
    }
    return null;
  }

  private ActionListener createMergeTheirsForFile(final SVNTreeConflictDescription description) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        new MergeFromTheirsResolver(myVcs, description, myChange, myCommittedRevision).execute();
      }
    };
  }

  public static String filePath(FilePath newFilePath) {
    return newFilePath.getName() +
    " (" +
    newFilePath.getParentPath().getPath() +
    ")";
  }

  private static ActionListener createBoth(SVNTreeConflictDescription description) {
    return null;
  }

  private static void enableAndSetListener(final ActionListener al, final JButton b) {
    if (al == null) {
      b.setEnabled(false);
    }
    else {
      b.addActionListener(al);
    }
  }

  private void addSide(JPanel main,
                       GridBagConstraints gb,
                       ConflictSidePresentation before,
                       SVNConflictVersion leftVersion, final String name, boolean directory) {
    final String leftPresentation = leftVersion == null ? name + ": (" + (directory ? "directory" : "file") +
      (myChange.getBeforeRevision() == null ? ") added" : ") unversioned") :
                                    name + ": " + FileUtil.toSystemIndependentName(SVNTreeConflictUtil.getHumanReadableConflictVersion(leftVersion));
    gb.insets.top = 10;
    main.add(new JLabel(leftPresentation), gb);
    ++ gb.gridy;
    gb.insets.top = 0;

    if (before != null) {
      JPanel panel = before.createPanel();
      if (panel != null) {
        //gb.fill = GridBagConstraints.HORIZONTAL;
        main.add(panel, gb);
        //gb.fill = GridBagConstraints.NONE;
        ++ gb.gridy;
      }
    }
  }

  @Override
  protected void disposeImpl() {
    Disposer.dispose(myChildDisposables);
  }

  @Override
  public void away() {
  }

  private interface ConflictSidePresentation extends Disposable {
    JPanel createPanel();
    void load() throws SVNException, VcsException;
  }

  private static class EmptyConflictSide implements ConflictSidePresentation {
    private static final EmptyConflictSide ourInstance = new EmptyConflictSide();

    public static EmptyConflictSide getInstance() {
      return ourInstance;
    }

    @Override
    public JPanel createPanel() {
      return null;
    }

    @Override
    public void dispose() {
    }

    @Override
    public void load() throws SVNException {
    }
  }

  private abstract static class AbstractConflictSide<T> implements ConflictSidePresentation, Convertor<T, VcsRevisionNumber> {
    protected final Project myProject;
    protected final SVNConflictVersion myVersion;

    private AbstractConflictSide(Project project, SVNConflictVersion version) {
      myProject = project;
      myVersion = version;
    }
  }

  private static class HistoryConflictSide extends AbstractConflictSide<VcsFileRevision> {
    public static final int LIMIT = 10;
    private final VcsAppendableHistoryPartnerAdapter mySessionAdapter;
    private final SvnHistoryProvider myProvider;
    private final FilePath myPath;
    private final SvnVcs myVcs;
    private final SVNRevision myPeg;
    private FileHistoryPanelImpl myFileHistoryPanel;
    private TLongArrayList myListToReportLoaded;

    private HistoryConflictSide(SvnVcs vcs, SVNConflictVersion version, final SVNRevision peg) throws VcsException {
      super(vcs.getProject(), version);
      myVcs = vcs;
      myPeg = peg;
      try {
        myPath = FilePathImpl.createNonLocal(
          version.getRepositoryRoot().appendPath(FileUtil.toSystemIndependentName(version.getPath()), true).toString(), SVNNodeKind.DIR.equals(version.getKind()));
      }
      catch (SVNException e) {
        throw new VcsException(e);
      }

      mySessionAdapter = new VcsAppendableHistoryPartnerAdapter();
      /*mySessionAdapter.reportCreatedEmptySession(new SvnHistorySession(myVcs, Collections.<VcsFileRevision>emptyList(),
        myPath, SvnUtil.checkRepositoryVersion15(myVcs, version.getPath()), null, true));*/
      myProvider = (SvnHistoryProvider) myVcs.getVcsHistoryProvider();
    }

    public void setListToReportLoaded(TLongArrayList listToReportLoaded) {
      myListToReportLoaded = listToReportLoaded;
    }

    @Override
    public VcsRevisionNumber convert(VcsFileRevision o) {
      return o.getRevisionNumber();
    }

    @Override
    public void load() throws SVNException, VcsException {
      SVNRevision from = SVNRevision.create(myVersion.getPegRevision());
      if (myPeg == null) {
        // just a portion of history
        myProvider.reportAppendableHistory(myPath, mySessionAdapter, from, myPeg, LIMIT, myPeg, true);
      } else {
        myProvider.reportAppendableHistory(myPath, mySessionAdapter, from, myPeg, 0, myPeg, true);
      }
      VcsAbstractHistorySession session = mySessionAdapter.getSession();
      if (myListToReportLoaded != null && session != null) {
        List<VcsFileRevision> list = session.getRevisionList();
        for (VcsFileRevision revision : list) {
          myListToReportLoaded.add(((SvnRevisionNumber) revision.getRevisionNumber()).getRevision().getNumber());
        }
      }
    }

    @Override
    public void dispose() {
      if (myFileHistoryPanel != null) {
        myFileHistoryPanel.dispose();
      }
    }

    @Override
    public JPanel createPanel() {
      VcsAbstractHistorySession session = mySessionAdapter.getSession();
      if (session == null) return EmptyConflictSide.getInstance().createPanel();
      List<VcsFileRevision> list = session.getRevisionList();
      if (list.isEmpty()) {
        return EmptyConflictSide.getInstance().createPanel();
      }
      VcsFileRevision last = null;
      if (! list.isEmpty() && myPeg == null && list.size() == LIMIT ||
          myPeg != null && myPeg.getNumber() > 0 &&
          myPeg.equals(((SvnRevisionNumber) list.get(list.size() - 1).getRevisionNumber()).getRevision())) {
        last = list.remove(list.size() - 1);
      }
      myFileHistoryPanel = new FileHistoryPanelImpl(myVcs, myPath, session, myProvider, null, new FileHistoryRefresherI() {
        @Override
        public void run(boolean isRefresh, boolean canUseCache) {
          //we will not refresh
        }

        @Override
        public boolean isFirstTime() {
          return false;
        }
      }, true);
      myFileHistoryPanel.setBottomRevisionForShowDiff(last);
      myFileHistoryPanel.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
      return myFileHistoryPanel;
    }
  }

  /*private static class HistoryAsBrowseChangesConflictSide implements AbstractConflictSide<Object> {
    public HistoryAsBrowseChangesConflictSide(Project project, SVNConflictVersion version) {
      //To change body of created methods use File | Settings | File Templates.
    }

    @Override
    public JPanel createPanel() {
      return null;
    }
  }*/
}
