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

import com.intellij.history.LocalHistory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CollectingContentIterator;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Convertor;
import com.intellij.util.continuation.*;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.VcsBackgroundTask;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.TLongArrayList;
import gnu.trove.TLongProcedure;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.history.*;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.wc.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
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
  private final List<Disposable> myChildDisposables;
  private final TLongArrayList myRightRevisionsList;

  public TreeConflictRefreshablePanel(Project project, String loadingTitle, BackgroundTaskQueue queue, Change change) {
    super(project, loadingTitle, queue);
    myVcs = SvnVcs.getInstance(project);
    assert change instanceof ConflictedSvnChange;
    myChange = (ConflictedSvnChange) change;
    myPath = ChangesUtil.getFilePath(myChange);
    myChildDisposables = new ArrayList<Disposable>();
    myRightRevisionsList = new TLongArrayList();
  }

  @Override
  public boolean isStillValid(final Change change) {
    return change.isTreeConflict() && change instanceof ConflictedSvnChange &&
           descriptionsEqual(((ConflictedSvnChange)change).getBeforeDescription(), myChange.getBeforeDescription());
  }

  private boolean descriptionsEqual(SVNTreeConflictDescription d1, SVNTreeConflictDescription d2) {
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

  private boolean compareConflictVersion(SVNConflictVersion v1, SVNConflictVersion v2) {
    if (v1 == null && v2 == null) return true;
    if (v1 == null && v2 != null || v1 != null && v2 == null) return false;
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
          if (myCommittedRevision != null && myCommittedRevision.getRevision().getNumber() < committed) {
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

  private boolean isDifferentURLs(SVNTreeConflictDescription description) {
    return description.getSourceLeftVersion() != null && description.getSourceRightVersion() != null &&
                ! Comparing.equal(description.getSourceLeftVersion().getPath(), description.getSourceRightVersion().getPath());
  }

  private ConflictSidePresentation createSide(SVNConflictVersion version, final SVNRevision untilThisOther, final boolean isLeft) throws VcsException {
    if (version == null) return EmptyConflictSide.getInstance();
    SvnRevisionNumber number = null;
    if (myChange.getBeforeRevision() != null && myCommittedRevision != null) {
      number = (SvnRevisionNumber) myCommittedRevision;
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
                               (new StringBuilder(" (current: ")
                                  .append(myChange.getBeforeRevision().getRevisionNumber().asString()).append(", committed: ")
                                  .append(myCommittedRevision.asString()).append(")").toString());
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
      if (myChange.getBeforeDescription() != null) {
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
        final FilePath oldFilePath = myChange.getBeforeRevision().getFile();
        final FilePath newFilePath = myChange.getAfterRevision().getFile();
        int ok = Messages.showOkCancelDialog(myVcs.getProject(),
                                             (myChange.isMoved() ?
                                              SvnBundle.message("confirmation.resolve.tree.conflict.merge.moved", filePath(oldFilePath),
                                                                filePath(newFilePath)) :
                                              SvnBundle.message("confirmation.resolve.tree.conflict.merge.renamed", filePath(oldFilePath),
                                                                filePath(newFilePath))),
                                             TITLE, Messages.getQuestionIcon());
        if (Messages.OK != ok) return;

        FileDocumentManager.getInstance().saveAllDocuments();
        final String name = "Merge changes from theirs for: " + filePath(oldFilePath);

        final GatheringContinuationContext cc = new GatheringContinuationContext();
        cc.addExceptionHandler(VcsException.class, new Consumer<VcsException>() {
          @Override
          public void consume(VcsException e) {
            AbstractVcsHelper.getInstance(myVcs.getProject()).showErrors(Collections.singletonList(e), name);
          }
        });
        cc.next(new TaskDescriptor("Creating patch for theirs changes", Where.POOLED) {
          @Override
          public void run(ContinuationContext context) {
            try {
              ProgressManager.progress("Getting base and theirs revisions content");
              final List<Change> changes = new SmartList<Change>();

              if (SVNNodeKind.DIR.equals(description.getNodeKind())) {
                long max = description.getSourceRightVersion().getPegRevision();
                long min = description.getSourceLeftVersion().getPegRevision();

                final ChangeBrowserSettings settings = new ChangeBrowserSettings();
                settings.USE_CHANGE_BEFORE_FILTER = settings.USE_CHANGE_AFTER_FILTER = true;
                settings.CHANGE_BEFORE = "" + max;
                settings.CHANGE_AFTER = "" + min;
                final List<SvnChangeList> committedChanges = myVcs.getCachingCommittedChangesProvider().getCommittedChanges(
                  settings, new SvnRepositoryLocation(description.getSourceRightVersion().getRepositoryRoot().toString()), 0);
                final List<CommittedChangeList> lst = new ArrayList<CommittedChangeList>(committedChanges.size() - 1);
                for (SvnChangeList change : committedChanges) {
                  if (change.getNumber() == min) {
                    continue;
                  }
                  lst.add(change);
                }
                final List<Change> changesForPatch = CommittedChangesTreeBrowser.collectChanges(lst, true);
                for (Change change : changesForPatch) {
                  ContentRevision before = null;
                  ContentRevision after = null;
                  if (change.getBeforeRevision() != null) {
                    before = new SimpleContentRevision(change.getBeforeRevision().getContent(),
                      rebasePath(oldFilePath, newFilePath, change.getBeforeRevision().getFile()),
                      change.getBeforeRevision().getRevisionNumber().asString());
                  }
                  if (change.getAfterRevision() != null) {
                    after = new SimpleContentRevision(change.getAfterRevision().getContent(),
                      rebasePath(oldFilePath, newFilePath, change.getAfterRevision().getFile()),
                      change.getAfterRevision().getRevisionNumber().asString());
                  }
                  changes.add(new Change(before, after));
                }
              } else {
                final SvnContentRevision base = SvnContentRevision.createBaseRevision(myVcs, newFilePath, myCommittedRevision.getRevision());
                final SvnContentRevision remote = SvnContentRevision.createRemote(myVcs, oldFilePath,
                                                                                  SVNRevision.create(
                                                                                    description.getSourceRightVersion().getPegRevision()));
                final ContentRevision newBase = new SimpleContentRevision(base.getContent(), newFilePath, base.getRevisionNumber().asString());
                final ContentRevision newRemote = new SimpleContentRevision(remote.getContent(), newFilePath, remote.getRevisionNumber().asString());
                changes.add(new Change(newBase, newRemote));
              }

              mergeFromTheirs(context, newFilePath, oldFilePath, description, changes);
            }
            catch (VcsException e1) {
              context.handleException(e1);
            }
          }
        });
        final Continuation fragmented = Continuation.createFragmented(myVcs.getProject(), false);
        fragmented.run(cc.getList());
      }
    };
  }

  private FilePath rebasePath(final FilePath oldBase, final FilePath newBase, final FilePath path) {
    final String relativePath = FileUtil.getRelativePath(oldBase.getPath(), path.getPath(), File.separatorChar);
    //if (StringUtil.isEmptyOrSpaces(relativePath)) return path;
    return ((FilePathImpl) newBase).createChild(relativePath, path.isDirectory());
  }

  private void mergeFromTheirs(ContinuationContext context, final FilePath newFilePath, final FilePath oldFilePath,
                               final SVNTreeConflictDescription description, final List<Change> changes) throws VcsException {

    ProgressManager.progress("Creating patch for theirs changes");
    final VirtualFile baseForPatch = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return ChangesUtil.findValidParent(newFilePath);
      }
    });
    final Project project = myVcs.getProject();
    final List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(project, changes, baseForPatch.getPath(), false);

    ProgressManager.progress("Applying patch to " + newFilePath.getPath());
    final ChangeListManager clManager = ChangeListManager.getInstance(project);
    final LocalChangeList changeList = clManager.getChangeList(myChange);
    final PatchApplier<BinaryFilePatch> patchApplier =
      new PatchApplier<BinaryFilePatch>(project, baseForPatch, patches, changeList, null, null);
    patchApplier.scheduleSelf(false, context, true);
    context.last(new TaskDescriptor("Accepting working state", Where.POOLED) {
      @Override
      public void run(ContinuationContext context) {
        try {
          new SvnTreeConflictResolver(myVcs, oldFilePath, myCommittedRevision, null).resolveSelectMineFull(description);
        }
        catch (VcsException e1) {
          context.handleException(e1);
        }
      }
    });
    context.last(new TaskDescriptor("", Where.AWT) {
      @Override
      public void run(ContinuationContext context) {
        VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), "Theirs changes merged for " + filePath(myPath), MessageType.INFO);
      }
    });
  }

  public static String filePath(FilePath newFilePath) {
    return newFilePath.getName() +
    " (" +
    newFilePath.getParentPath().getPath() +
    ")";
  }

  private ActionListener createBoth(SVNTreeConflictDescription description) {
    return null;
  }

  private void enableAndSetListener(final ActionListener al, final JButton b) {
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
      (name + ": " + FileUtil.toSystemIndependentName(SVNTreeConflictUtil.getHumanReadableConflictVersion(leftVersion)));
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
    for (Disposable disposable : myChildDisposables) {
      Disposer.dispose(disposable);
    }
  }

  @Override
  public void away() {
  }

  private interface ConflictSidePresentation extends Disposable {
    JPanel createPanel();
    void load() throws SVNException, VcsException;
  }

  private static class EmptyConflictSide implements ConflictSidePresentation {
    private final static EmptyConflictSide ourInstance = new EmptyConflictSide();

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

  private static abstract class AbstractConflictSide<T> implements ConflictSidePresentation, Convertor<T, VcsRevisionNumber> {
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
          (myPeg != null && myPeg.getNumber() > 0 &&
          myPeg.equals(((SvnRevisionNumber) list.get(list.size() - 1).getRevisionNumber()).getRevision()))) {
        last = list.remove(list.size() - 1);
      }
      myFileHistoryPanel = new FileHistoryPanelImpl(myVcs, myPath, session, myProvider, null, new FileHistoryRefresherI() {
        @Override
        public void run(boolean isRefresh) {
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
