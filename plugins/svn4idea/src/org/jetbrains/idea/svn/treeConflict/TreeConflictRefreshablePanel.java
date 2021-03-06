// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.BeforeAfter;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.VcsBackgroundTask;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Objects;

import static com.intellij.openapi.application.ModalityState.defaultModalityState;
import static com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView;
import static com.intellij.vcsUtil.VcsUtil.getFilePathOnNonLocal;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.history.SvnHistorySession.getCurrentCommittedRevision;

public final class TreeConflictRefreshablePanel implements Disposable {
  private final ConflictedSvnChange myChange;
  private final SvnVcs myVcs;
  private SvnRevisionNumber myCommittedRevision;
  private final FilePath myPath;
  private final CompositeDisposable myChildDisposables = new CompositeDisposable();
  private final LongArrayList myRightRevisionsList;
  @NotNull private final JBLoadingPanel myDetailsPanel;
  @NotNull private final BackgroundTaskQueue myQueue;
  private volatile ProgressIndicator myIndicator = new EmptyProgressIndicator();

  public TreeConflictRefreshablePanel(@NotNull Project project, @NotNull BackgroundTaskQueue queue, @NotNull ConflictedSvnChange change) {
    myVcs = SvnVcs.getInstance(project);
    myChange = change;
    myPath = ChangesUtil.getFilePath(myChange);
    myRightRevisionsList = new LongArrayList();

    myQueue = queue;
    myDetailsPanel = new JBLoadingPanel(new BorderLayout(), this);
  }

  public static boolean descriptionsEqual(TreeConflictDescription d1, TreeConflictDescription d2) {
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
    if (!v1.getNodeKind().equals(v2.getNodeKind())) return false;
    if (!v1.getPath().equals(v2.getPath())) return false;
    if (v1.getPegRevision() != v2.getPegRevision()) return false;
    if (!Comparing.equal(v1.getRepositoryRoot(), v2.getRepositoryRoot())) return false;
    return true;
  }

  @NotNull
  public JPanel getPanel() {
    return myDetailsPanel;
  }

  @RequiresBackgroundThread
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
           !Objects.equals(description.getSourceLeftVersion().getPath(), description.getSourceRightVersion().getPath());
  }

  @NotNull
  private ConflictSidePresentation createSide(@Nullable ConflictVersion version, @Nullable Revision untilThisOther, boolean isLeft)
    throws VcsException {
    ConflictSidePresentation result = EmptyConflictSide.INSTANCE;
    if (version != null &&
        (myChange.getBeforeRevision() == null ||
         myCommittedRevision == null ||
         !isLeft ||
         !myCommittedRevision.getRevision().isValid() ||
         myCommittedRevision.getRevision().getNumber() != version.getPegRevision())) {

      FilePath remotePath =
        getFilePathOnNonLocal(version.getRepositoryRoot().appendPath(version.getPath(), false).toDecodedString(), version.isDirectory());
      HistoryConflictSide side = new HistoryConflictSide(myVcs, remotePath, Revision.of(version.getPegRevision()), untilThisOther);
      if (untilThisOther != null && !isLeft) {
        side.setListToReportLoaded(myRightRevisionsList);
      }
      result = side;
    }
    myChildDisposables.add(result);
    return result;
  }

  @RequiresEdt
  public void refresh() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myDetailsPanel.startLoading();
    Loader task = new Loader(myVcs.getProject());
    myIndicator = new BackgroundableProcessIndicator(task);
    myQueue.run(task, defaultModalityState(), myIndicator);
  }

  @RequiresEdt
  private JPanel dataToPresentation(BeforeAfter<BeforeAfter<ConflictSidePresentation>> data) {
    final JPanel wrapper = new JPanel(new BorderLayout());
    final JPanel main = new JPanel(new GridBagLayout());

    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                         JBUI.insets(1), 0, 0);
    String pathDescription = myCommittedRevision == null
                             ? myPath.getName()
                             : message("label.path.revisions.info", myPath.getName(),
                                       myChange.getBeforeRevision().getRevisionNumber().asString(), myCommittedRevision.asString());
    final JLabel name = new JLabel(pathDescription);
    name.setFont(name.getFont().deriveFont(Font.BOLD));
    gb.insets.top = 5;
    main.add(name, gb);
    ++gb.gridy;
    gb.insets.top = 10;
    appendDescription(myChange.getBeforeDescription(), main, gb, data.getBefore());
    appendDescription(myChange.getAfterDescription(), main, gb, data.getAfter());
    wrapper.add(main, BorderLayout.NORTH);
    return wrapper;
  }

  private void appendDescription(TreeConflictDescription description,
                                 JPanel main,
                                 GridBagConstraints gb,
                                 BeforeAfter<ConflictSidePresentation> ba) {
    if (description == null) return;
    JLabel descriptionLbl = new JLabel(description.toPresentableString());
    descriptionLbl.setForeground(JBColor.RED);
    main.add(descriptionLbl, gb);
    ++gb.gridy;
    //buttons
    gb.insets.top = 0;
    addResolveButtons(description, main, gb);

    addSide(main, gb, ba.getBefore(),
            message("label.conflict.left.side", ConflictSidePresentation.getDescription(description.getSourceLeftVersion(), myChange)));
    addSide(main, gb, ba.getAfter(),
            message("label.conflict.right.side", ConflictSidePresentation.getDescription(description.getSourceRightVersion(), myChange)));
  }

  private void addResolveButtons(TreeConflictDescription description, JPanel main, GridBagConstraints gb) {
    JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));

    final JButton merge = new JButton(message("button.resolve.conflict.merge"));
    final JButton left = new JButton(message("button.resolve.conflict.accept.yours"));
    final JButton right = new JButton(message("button.resolve.conflict.accept.theirs"));
    enableAndSetListener(createMerge(description), merge);
    enableAndSetListener(createLeft(description), left);
    enableAndSetListener(createRight(description), right);

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
        int ok = Messages.showOkCancelDialog(
          myVcs.getProject(),
          message("dialog.message.accept.theirs.for.path", filePath(myPath)),
          message("dialog.title.resolve.tree.conflict"),
          Messages.getQuestionIcon()
        );
        if (Messages.OK != ok) return;
        FileDocumentManager.getInstance().saveAllDocuments();
        final Paths paths = getPaths(description);
        ProgressManager.getInstance().run(
          new VcsBackgroundTask<>(
            myVcs.getProject(),
            message("progress.title.accepting.theirs.for.path", filePath(paths.myMainPath)),
            PerformInBackgroundOption.ALWAYS_BACKGROUND,
            Collections.singletonList(description),
            true
          ) {

            @Override
            protected void process(TreeConflictDescription d) throws VcsException {
              new SvnTreeConflictResolver(myVcs, paths.myMainPath, paths.myAdditionalPath).resolveSelectTheirsFull();
            }

            @Override
            public void onSuccess() {
              super.onSuccess();
              if (executedOk()) {
                showOverChangesView(myProject, message("message.theirs.accepted.for.file", filePath(paths.myMainPath)), MessageType.INFO);
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

  private static final class Paths {
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
        int ok = Messages.showOkCancelDialog(
          myVcs.getProject(),
          message("dialog.message.accept.yours.for.path", filePath(myPath)),
          message("dialog.title.resolve.tree.conflict"),
          Messages.getQuestionIcon()
        );
        if (Messages.OK != ok) return;
        FileDocumentManager.getInstance().saveAllDocuments();
        final Paths paths = getPaths(description);
        ProgressManager.getInstance().run(
          new VcsBackgroundTask<>(
            myVcs.getProject(),
            message("progress.title.accepting.yours.for.path", filePath(paths.myMainPath)),
            PerformInBackgroundOption.ALWAYS_BACKGROUND,
            Collections.singletonList(description),
            true
          ) {

            @Override
            protected void process(TreeConflictDescription d) throws VcsException {
              new SvnTreeConflictResolver(myVcs, paths.myMainPath, paths.myAdditionalPath).resolveSelectMineFull();
            }

            @Override
            public void onSuccess() {
              super.onSuccess();
              if (executedOk()) {
                showOverChangesView(myProject, message("message.yours.accepted.for.file", filePath(paths.myMainPath)), MessageType.INFO);
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
    return newFilePath.getName() + " (" + Objects.requireNonNull(newFilePath.getParentPath()).getPath() + ")";
  }

  private static void enableAndSetListener(final ActionListener al, final JButton b) {
    if (al == null) {
      b.setEnabled(false);
    }
    else {
      b.addActionListener(al);
    }
  }

  private static void addSide(JPanel main,
                              GridBagConstraints gb,
                              ConflictSidePresentation conflictSide,
                              @NlsContexts.Label String description) {
    gb.insets.top = 10;
    main.add(new JBLabel(description), gb);
    ++gb.gridy;
    gb.insets.top = 0;

    if (conflictSide != null) {
      JPanel panel = conflictSide.createPanel();
      if (panel != null) {
        main.add(panel, gb);
        ++gb.gridy;
      }
    }
  }

  @Override
  public void dispose() {
    myIndicator.cancel();
    Disposer.dispose(myChildDisposables);
  }

  private final class Loader extends Task.Backgroundable {
    private BeforeAfter<BeforeAfter<ConflictSidePresentation>> myData;
    private VcsException myException;

    private Loader(@Nullable Project project) {
      super(project, message("progress.title.loading.tree.conflict.details"), false);
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
        showOverChangesView(myProject, myException.getMessage(), MessageType.ERROR);
      }
      else {
        myDetailsPanel.add(dataToPresentation(myData));
        myDetailsPanel.stopLoading();
      }
    }
  }
}
