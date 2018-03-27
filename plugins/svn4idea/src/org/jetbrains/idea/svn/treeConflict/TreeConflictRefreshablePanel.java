/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.VcsBackgroundTask;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.ConflictedSvnChange;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.conflict.ConflictAction;
import org.jetbrains.idea.svn.conflict.ConflictReason;
import org.jetbrains.idea.svn.conflict.ConflictVersion;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.application.ModalityState.defaultModalityState;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.vcsUtil.VcsUtil.getFilePathOnNonLocal;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.history.SvnHistorySession.getCurrentCommittedRevision;

public class TreeConflictRefreshablePanel implements Disposable {

  public static final String TITLE = "Resolve tree conflict";
  private final ConflictedSvnChange myChange;
  private final SvnVcs myVcs;
  private SvnRevisionNumber myCommittedRevision;
  private FilePath myPath;
  private final CompositeDisposable myChildDisposables = new CompositeDisposable();
  private final TLongArrayList myRightRevisionsList;
  @NotNull private final String myLoadingTitle;
  @NotNull private final JBLoadingPanel myDetailsPanel;
  @NotNull private final BackgroundTaskQueue myQueue;
  private volatile ProgressIndicator myIndicator = new EmptyProgressIndicator();

  public TreeConflictRefreshablePanel(@NotNull Project project,
                                      @NotNull String loadingTitle,
                                      @NotNull BackgroundTaskQueue queue,
                                      Change change) {
    myVcs = SvnVcs.getInstance(project);
    assert change instanceof ConflictedSvnChange;
    myChange = (ConflictedSvnChange)change;
    myPath = ChangesUtil.getFilePath(myChange);
    myRightRevisionsList = new TLongArrayList();

    myLoadingTitle = loadingTitle;
    myQueue = queue;
    myDetailsPanel = new JBLoadingPanel(new BorderLayout(), this);
  }

  public static boolean descriptionsEqual(TreeConflictDescription d1, TreeConflictDescription d2) {
    if (d1.isPropertyConflict() != d2.isPropertyConflict()) return false;
    if (d1.isTextConflict() != d2.isTextConflict()) return false;
    if (d1.isTreeConflict() != d2.isTreeConflict()) return false;

    if (!d1.getOperation().equals(d2.getOperation())) return false;
    if (!d1.getConflictAction().equals(d2.getConflictAction())) return false;
    if (!Comparing.equal(d1.getConflictReason(), d2.getConflictReason())) return false;
    if (!Comparing.equal(d1.getPath(), d2.getPath())) return false;
    if (!Comparing.equal(d1.getNodeKind(), d2.getNodeKind())) return false;
    if (!compareConflictVersion(d1.getSourceLeftVersion(), d2.getSourceLeftVersion())) return false;
    if (!compareConflictVersion(d1.getSourceRightVersion(), d2.getSourceRightVersion())) return false;
    return true;
  }

  private static boolean compareConflictVersion(ConflictVersion v1, ConflictVersion v2) {
    if (v1 == null && v2 == null) return true;
    if (v1 == null || v2 == null) return false;
    if (!v1.getKind().equals(v2.getKind())) return false;
    if (!v1.getPath().equals(v2.getPath())) return false;
    if (v1.getPegRevision() != v2.getPegRevision()) return false;
    if (!Comparing.equal(v1.getRepositoryRoot(), v2.getRepositoryRoot())) return false;
    return true;
  }

  @NotNull
  public JPanel getPanel() {
    return myDetailsPanel;
  }

  @CalledInBackground
  private BeforeAfter<ConflictSidePresentation> processDescription(@NotNull ProgressIndicator indicator,
                                                                   TreeConflictDescription description) throws VcsException {
    if (description == null) return null;
    if (myChange.getBeforeRevision() != null) {
      myCommittedRevision = (SvnRevisionNumber)getCurrentCommittedRevision(myVcs, myChange.getBeforeRevision() != null ? myChange
        .getBeforeRevision().getFile().getIOFile() : myPath.getIOFile());
    }

    indicator.checkCanceled();

    ConflictSidePresentation leftSide;
    ConflictSidePresentation rightSide;
    if (isDifferentURLs(description)) {
      leftSide = createSide(description.getSourceLeftVersion(), null, true);
      rightSide = createSide(description.getSourceRightVersion(), null, false);
    }
    else { //only one side
      leftSide = createSide(null, null, true);
      rightSide = createSide(description.getSourceRightVersion(), getPegRevisionFromLeftSide(description), false);
    }
    indicator.checkCanceled();
    leftSide.load();
    indicator.checkCanceled();
    rightSide.load();
    indicator.checkCanceled();

    return new BeforeAfter<>(leftSide, rightSide);
  }

  @Nullable
  private Revision getPegRevisionFromLeftSide(@NotNull TreeConflictDescription description) {
    Revision result = null;
    if (description.getSourceLeftVersion() != null) {
      long committed = description.getSourceLeftVersion().getPegRevision();
      if (myCommittedRevision != null &&
          myCommittedRevision.getRevision().getNumber() < committed &&
          myCommittedRevision.getRevision().isValid()) {
        committed = myCommittedRevision.getRevision().getNumber();
      }
      result = Revision.of(committed);
    }
    return result;
  }

  private static boolean isDifferentURLs(TreeConflictDescription description) {
    return description.getSourceLeftVersion() != null && description.getSourceRightVersion() != null &&
           !Comparing.equal(description.getSourceLeftVersion().getPath(), description.getSourceRightVersion().getPath());
  }

  @NotNull
  private ConflictSidePresentation createSide(@Nullable ConflictVersion version, @Nullable Revision untilThisOther, boolean isLeft)
    throws VcsException {
    ConflictSidePresentation result = EmptyConflictSide.getInstance();
    if (version != null &&
        (myChange.getBeforeRevision() == null ||
         myCommittedRevision == null ||
         !isLeft ||
         !myCommittedRevision.getRevision().isValid() ||
         myCommittedRevision.getRevision().getNumber() != version.getPegRevision())) {
      HistoryConflictSide side = new HistoryConflictSide(myVcs, version, untilThisOther);
      if (untilThisOther != null && !isLeft) {
        side.setListToReportLoaded(myRightRevisionsList);
      }
      result = side;
    }
    myChildDisposables.add(result);
    return result;
  }

  @CalledInAwt
  public void refresh() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myDetailsPanel.startLoading();
    Loader task = new Loader(myVcs.getProject(), myLoadingTitle);
    myIndicator = new BackgroundableProcessIndicator(task);
    myQueue.run(task, defaultModalityState(), myIndicator);
  }

  @CalledInAwt
  protected JPanel dataToPresentation(BeforeAfter<BeforeAfter<ConflictSidePresentation>> data) {
    final JPanel wrapper = new JPanel(new BorderLayout());
    final JPanel main = new JPanel(new GridBagLayout());

    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                         JBUI.insets(1), 0, 0);
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
    ++gb.gridy;
    gb.insets.top = 10;
    appendDescription(myChange.getBeforeDescription(), main, gb, data.getBefore(), myPath.isDirectory());
    appendDescription(myChange.getAfterDescription(), main, gb, data.getAfter(), myPath.isDirectory());
    wrapper.add(main, BorderLayout.NORTH);
    return wrapper;
  }

  private void appendDescription(TreeConflictDescription description,
                                 JPanel main,
                                 GridBagConstraints gb,
                                 BeforeAfter<ConflictSidePresentation> ba, boolean directory) {
    if (description == null) return;
    JLabel descriptionLbl = new JLabel(description.toPresentableString());
    descriptionLbl.setForeground(JBColor.RED);
    main.add(descriptionLbl, gb);
    ++gb.gridy;
    //buttons
    gb.insets.top = 0;
    addResolveButtons(description, main, gb);

    addSide(main, gb, ba.getBefore(), description.getSourceLeftVersion(), "Left", directory);
    addSide(main, gb, ba.getAfter(), description.getSourceRightVersion(), "Right", directory);
  }

  private void addResolveButtons(TreeConflictDescription description, JPanel main, GridBagConstraints gb) {
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
    ++gb.gridy;
  }

  private ActionListener createRight(final TreeConflictDescription description) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int ok = Messages.showOkCancelDialog(myVcs.getProject(), "Accept theirs for " + filePath(myPath) + "?",
                                             TITLE, Messages.getQuestionIcon());
        if (Messages.OK != ok) return;
        FileDocumentManager.getInstance().saveAllDocuments();
        final Paths paths = getPaths(description);
        ProgressManager.getInstance().run(
          new VcsBackgroundTask<TreeConflictDescription>(myVcs.getProject(), "Accepting theirs for: " + filePath(paths.myMainPath),
                                                         PerformInBackgroundOption.ALWAYS_BACKGROUND,
                                                         Collections.singletonList(description),
                                                         true) {
            @Override
            protected void process(TreeConflictDescription d) throws VcsException {
              new SvnTreeConflictResolver(myVcs, paths.myMainPath, paths.myAdditionalPath).resolveSelectTheirsFull();
            }

            @Override
            public void onSuccess() {
              super.onSuccess();
              if (executedOk()) {
                VcsBalloonProblemNotifier
                  .showOverChangesView(myProject, "Theirs accepted for " + filePath(paths.myMainPath), MessageType.INFO);
              }
            }
          });
      }
    };
  }

  private Paths getPaths(final TreeConflictDescription description) {
    FilePath mainPath;
    FilePath additionalPath = null;
    if (myChange.isMoved() || myChange.isRenamed()) {
      if (ConflictAction.ADD.equals(description.getConflictAction())) {
        mainPath = myChange.getAfterRevision().getFile();
        additionalPath = myChange.getBeforeRevision().getFile();
      }
      else {
        mainPath = myChange.getBeforeRevision().getFile();
        additionalPath = myChange.getAfterRevision().getFile();
      }
    }
    else {
      mainPath = myChange.getBeforeRevision() != null ? myChange.getBeforeRevision().getFile() : myChange.getAfterRevision().getFile();
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

  private ActionListener createLeft(final TreeConflictDescription description) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int ok = Messages.showOkCancelDialog(myVcs.getProject(), "Accept yours for " + filePath(myPath) + "?",
                                             TITLE, Messages.getQuestionIcon());
        if (Messages.OK != ok) return;
        FileDocumentManager.getInstance().saveAllDocuments();
        final Paths paths = getPaths(description);
        ProgressManager.getInstance().run(
          new VcsBackgroundTask<TreeConflictDescription>(myVcs.getProject(), "Accepting yours for: " + filePath(paths.myMainPath),
                                                         PerformInBackgroundOption.ALWAYS_BACKGROUND,
                                                         Collections.singletonList(description),
                                                         true) {
            @Override
            protected void process(TreeConflictDescription d) throws VcsException {
              new SvnTreeConflictResolver(myVcs, paths.myMainPath, paths.myAdditionalPath).resolveSelectMineFull();
            }

            @Override
            public void onSuccess() {
              super.onSuccess();
              if (executedOk()) {
                VcsBalloonProblemNotifier
                  .showOverChangesView(myProject, "Yours accepted for " + filePath(paths.myMainPath), MessageType.INFO);
              }
            }
          });
      }
    };
  }

  private ActionListener createMerge(final TreeConflictDescription description) {
    if (isDifferentURLs(description)) {
      return null;
    }
    // my edit, theirs move or delete
    if (ConflictAction.EDIT.equals(description.getConflictAction()) && description.getSourceLeftVersion() != null &&
        ConflictReason.DELETED.equals(description.getConflictReason()) && (myChange.isMoved() || myChange.isRenamed()) &&
        myCommittedRevision != null) {
      if (myPath.isDirectory() == description.getSourceRightVersion().isDirectory()) {
        return createMergeTheirsForFile(description);
      }
    }
    return null;
  }

  private ActionListener createMergeTheirsForFile(final TreeConflictDescription description) {
    return e -> new MergeFromTheirsResolver(myVcs, description, myChange, myCommittedRevision).execute();
  }

  @NotNull
  public static String filePath(@NotNull FilePath newFilePath) {
    return newFilePath.getName() + " (" + notNull(newFilePath.getParentPath()).getPath() + ")";
  }

  private static ActionListener createBoth(TreeConflictDescription description) {
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
                       ConflictVersion leftVersion, final String name, boolean directory) {
    final String leftPresentation = leftVersion == null ? name + ": (" + (directory ? "directory" : "file") +
                                                          (myChange.getBeforeRevision() == null ? ") added" : ") unversioned") :
                                    name + ": " + toSystemIndependentName(ConflictVersion.toPresentableString(leftVersion));
    gb.insets.top = 10;
    main.add(new JLabel(leftPresentation), gb);
    ++gb.gridy;
    gb.insets.top = 0;

    if (before != null) {
      JPanel panel = before.createPanel();
      if (panel != null) {
        //gb.fill = GridBagConstraints.HORIZONTAL;
        main.add(panel, gb);
        //gb.fill = GridBagConstraints.NONE;
        ++gb.gridy;
      }
    }
  }

  @Override
  public void dispose() {
    myIndicator.cancel();
    Disposer.dispose(myChildDisposables);
  }

  private interface ConflictSidePresentation extends Disposable {
    JPanel createPanel();

    void load() throws VcsException;
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
    public void load() {
    }
  }

  private abstract static class AbstractConflictSide<T> implements ConflictSidePresentation, Convertor<T, VcsRevisionNumber> {
    protected final Project myProject;
    protected final ConflictVersion myVersion;

    private AbstractConflictSide(Project project, ConflictVersion version) {
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
    private final Revision myPeg;
    private FileHistoryPanelImpl myFileHistoryPanel;
    private TLongArrayList myListToReportLoaded;

    private HistoryConflictSide(SvnVcs vcs, ConflictVersion version, final Revision peg) throws VcsException {
      super(vcs.getProject(), version);
      myVcs = vcs;
      myPeg = peg;
      myPath = getFilePathOnNonLocal(append(version.getRepositoryRoot(), toSystemIndependentName(version.getPath()), true).toString(),
                                     version.isDirectory());

      mySessionAdapter = new VcsAppendableHistoryPartnerAdapter();
      /*mySessionAdapter.reportCreatedEmptySession(new SvnHistorySession(myVcs, Collections.<VcsFileRevision>emptyList(),
        myPath, SvnUtil.checkRepositoryVersion15(myVcs, version.getPath()), null, true));*/
      myProvider = (SvnHistoryProvider)myVcs.getVcsHistoryProvider();
    }

    public void setListToReportLoaded(TLongArrayList listToReportLoaded) {
      myListToReportLoaded = listToReportLoaded;
    }

    @Override
    public VcsRevisionNumber convert(VcsFileRevision o) {
      return o.getRevisionNumber();
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
      if (session == null) return EmptyConflictSide.getInstance().createPanel();
      List<VcsFileRevision> list = session.getRevisionList();
      if (list.isEmpty()) {
        return EmptyConflictSide.getInstance().createPanel();
      }
      VcsFileRevision last = null;
      if (!list.isEmpty() && myPeg == null && list.size() == LIMIT ||
          myPeg != null && myPeg.getNumber() > 0 &&
          myPeg.equals(((SvnRevisionNumber)list.get(list.size() - 1).getRevisionNumber()).getRevision())) {
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

  private class Loader extends Task.Backgroundable {
    private BeforeAfter<BeforeAfter<ConflictSidePresentation>> myData;
    private VcsException myException;

    private Loader(@Nullable Project project, @NotNull String title) {
      super(project, title, false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        myData = new BeforeAfter<>(processDescription(indicator, myChange.getBeforeDescription()),
                                   processDescription(indicator, myChange.getAfterDescription()));
      }
      catch (VcsException e) {
        myException = e;
      }
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, myException.getMessage(), MessageType.ERROR);
      }
      else {
        myDetailsPanel.add(dataToPresentation(myData));
        myDetailsPanel.stopLoading();
      }
    }
  }
}
