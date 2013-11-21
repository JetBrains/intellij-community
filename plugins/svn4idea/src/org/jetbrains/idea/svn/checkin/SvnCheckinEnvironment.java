/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FunctionUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.commandLine.SvnCommandLineStatusClient;
import org.jetbrains.idea.svn.commandLine.SvnCommitRunner;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class SvnCheckinEnvironment implements CheckinEnvironment {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment");
  private final SvnVcs mySvnVcs;

  public SvnCheckinEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
    return new KeepLocksComponent(panel);
  }

  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  @Nullable
  public String getHelpId() {
    return null;
  }


  private List<VcsException> commitInt(List<File> paths, final String comment, final boolean force, final boolean recursive,
                                       final Set<String> feedback) {
    final List<VcsException> exception = new ArrayList<VcsException>();
    final List<File> committables = getCommitables(paths);

    final SVNCommitClient committer = mySvnVcs.createCommitClient();

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    final Collection<VirtualFile> deletedFiles = new ArrayList<VirtualFile>();
    if (progress != null) {
      committer.setEventHandler(new ISVNEventHandler() {
        public void handleEvent(final SVNEvent event, double p) {
          final String path = SvnUtil.getPathForProgress(event);
          if (path == null) {
            return;
          }
          if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
            progress.setText2(SvnBundle.message("progress.text2.adding", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
            @NonNls final String filePath = "file://" + event.getFile().getAbsolutePath().replace(File.separatorChar, '/');
            VirtualFile vf = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
              @Nullable public VirtualFile compute() {
                return VirtualFileManager.getInstance().findFileByUrl(filePath);
              }
            });
            if (vf != null) {
              deletedFiles.add(vf);
            }
            progress.setText2(SvnBundle.message("progress.text2.deleting", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
            progress.setText2(SvnBundle.message("progress.text2.sending", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
            progress.setText2(SvnBundle.message("progress.text2.replacing", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
            progress.setText2(SvnBundle.message("progress.text2.transmitting.delta", path));
          }
          // do not need COMMIT_COMPLETED: same info is get another way
        }

        public void checkCancelled() throws SVNCancelException {
          try {
            progress.checkCanceled();
          }
          catch(ProcessCanceledException ex) {
            throw new SVNCancelException();
          }
        }
      });
    }

    if (progress != null) {
      doCommit(committables, committer, comment, force, exception, feedback);
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator p = ProgressManager.getInstance().getProgressIndicator();
          doCommit(committables, committer, comment, force, exception, feedback);
        }
      }, SvnBundle.message("progress.title.commit"), false, mySvnVcs.getProject());
    }
    else {
      doCommit(committables, committer, comment, force, exception, feedback);
    }

    for(VirtualFile f : deletedFiles) {
      f.putUserData(VirtualFile.REQUESTOR_MARKER, this);
    }
    return exception;
  }

  private void doCommit(List<File> committables,
                        SVNCommitClient committer,
                        String comment,
                        boolean force,
                        List<VcsException> exception, final Set<String> feedback) {
    final MultiMap<Pair<SVNURL,WorkingCopyFormat>,File> map = SvnUtil.splitIntoRepositoriesMap(mySvnVcs, committables, Convertor.SELF);
    for (Map.Entry<Pair<SVNURL, WorkingCopyFormat>, Collection<File>> entry : map.entrySet()) {
      doCommitOneRepo(entry.getValue(), committer, comment, force, exception, feedback, entry.getKey().getSecond(), entry.getKey().getFirst());
    }
  }

  private void doCommitOneRepo(Collection<File> committables,
                               SVNCommitClient committer,
                               String comment,
                               boolean force,
                               List<VcsException> exception, final Set<String> feedback, final WorkingCopyFormat format, SVNURL url) {
    if (committables.isEmpty()) {
      return;
    }
    if (WorkingCopyFormat.ONE_DOT_EIGHT.equals(format) || WorkingCopyFormat.ONE_DOT_SEVEN.equals(format) &&
        SvnConfiguration.getInstance(mySvnVcs.getProject()).isCommandLine() &&
        (SvnAuthenticationManager.HTTP.equals(url.getProtocol()) || SvnAuthenticationManager.HTTPS.equals(url.getProtocol()))) {
      doWithCommandLine(committables, comment, exception, feedback);
      return;
    }

    doWithSvnkit(committables, committer, comment, force, exception, feedback);
  }

  private void doWithSvnkit(Collection<File> committables,
                            SVNCommitClient committer,
                            String comment,
                            boolean force,
                            List<VcsException> exception, Set<String> feedback) {
    File[] pathsToCommit = committables.toArray(new File[committables.size()]);
    boolean keepLocks = SvnConfiguration.getInstance(mySvnVcs.getProject()).isKeepLocks();
    SVNCommitPacket[] commitPackets = null;
    SVNCommitInfo[] results;
    try {
      commitPackets = committer.doCollectCommitItems(pathsToCommit, keepLocks, force, SVNDepth.EMPTY, true, null);
      results = committer.doCommit(commitPackets, keepLocks, comment);
      commitPackets = null;
    }
    catch (SVNException e) {
      // exception on collecting commitables.
      exception.add(new VcsException(e));
      LOG.info(e);
      return;
    }
    finally {
      if (commitPackets != null) {
        for (int i = 0; i < commitPackets.length; i++) {
          SVNCommitPacket commitPacket = commitPackets[i];
          try {
            commitPacket.dispose();
          }
          catch (SVNException e) {
            //
          }
        }
      }
    }
    final StringBuffer committedRevisions = new StringBuffer();
    for (SVNCommitInfo result : results) {
      if (result.getErrorMessage() != null) {
        exception.add(new VcsException(result.getErrorMessage().getFullMessage()));
      }
      else if (result != SVNCommitInfo.NULL && result.getNewRevision() > 0) {
        if (committedRevisions.length() > 0) {
          committedRevisions.append(", ");
        }
        committedRevisions.append(result.getNewRevision());
      }
    }
    if (committedRevisions.length() > 0) {
      reportCommittedRevisions(feedback, committedRevisions.toString());
    }
  }

  private void doWithCommandLine(Collection<File> committables, String comment, List<VcsException> exception, Set<String> feedback) {
    // if directory renames were used, IDEA reports all files under them as moved, but for svn we can not pass some of them
    // to commit command - since not all paths are registered as changes -> so we need to filter these cases, but only if
    // there at least some child-parent relationships in passed paths
    try {
      committables = filterCommittables(committables);
    } catch (SVNException e) {
      exception.add(new VcsException(e));
      return;
    }

    IdeaCommitHandler handler = new IdeaCommitHandler(ProgressManager.getInstance().getProgressIndicator());

    try {
      SvnCommitRunner runner = new SvnCommitRunner(mySvnVcs, handler);
      final long revision =
        runner.commit(ArrayUtil.toObjectArray(committables, File.class), comment, SVNDepth.EMPTY, false, false, null, null);

      reportCommittedRevisions(feedback, String.valueOf(revision));
    }
    catch (VcsException e) {
      exception.add(e);
    }
  }

  private Collection<File> filterCommittables(Collection<File> committables) throws SVNException {
    final Set<File> childrenOfSomebody = new HashSet<File>();
    new AbstractFilterChildren<File>() {
      @Override
      protected void sortAscending(List<File> list) {
        Collections.sort(list);
      }

      @Override
      protected boolean isAncestor(File parent, File child) {
        final boolean isAncestor = FileUtil.isAncestor(parent, child, false);
        if (isAncestor) {
          childrenOfSomebody.add(child);
        }
        return isAncestor;
      }
    }.doFilter(new ArrayList<File>(committables));
    if (! childrenOfSomebody.isEmpty()) {
      final HashSet<File> result = new HashSet<File>(committables);
      result.removeAll(childrenOfSomebody);
      final SvnCommandLineStatusClient statusClient = new SvnCommandLineStatusClient(mySvnVcs);
      for (File file : childrenOfSomebody) {
        try {
          final SVNStatus status = statusClient.doStatus(file, false);
          if (status != null && ! SVNStatusType.STATUS_NONE.equals(status.getContentsStatus()) &&
              ! SVNStatusType.STATUS_UNVERSIONED.equals(status.getContentsStatus())) {
            result.add(file);
          }
        }
        catch (SVNException e) {
          // not versioned
          LOG.info(e);
          throw e;
        }
      }
      return result;
    }
    return committables;
  }

  private void reportCommittedRevisions(Set<String> feedback, String committedRevisions) {
    final Project project = mySvnVcs.getProject();
    final String message = SvnBundle.message("status.text.comitted.revision", committedRevisions);
    if (feedback == null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
                                                        public void run() {
                                                          new VcsBalloonProblemNotifier(project, message, MessageType.INFO).run();
                                                        }
                                                      }, new Condition<Object>() {
        @Override
        public boolean value(Object o) {
          return (! project.isOpen()) || project.isDisposed();
        }
      });
    } else {
      feedback.add("Subversion: " + message);
    }
  }

  private static class Adder {
    private final List<File> myResult = new ArrayList<File>();
    private final Set<String> myDuplicatesControlSet = new HashSet<String>();

    public void add(final File file) {
      final String path = file.getAbsolutePath();
      if (! myDuplicatesControlSet.contains(path)) {
        myResult.add(file);
        myDuplicatesControlSet.add(path);
      }
    }

    public List<File> getResult() {
      return myResult;
    }
  }

  private List<File> getCommitables(List<File> paths) {
    final Adder adder = new Adder();

    for (File path : paths) {
      File file = path.getAbsoluteFile();
      adder.add(file);
      if (file.getParentFile() != null) {
        addParents(file.getParentFile(), adder);
      }
    }
    return adder.getResult();
  }

  private void addParents(File file, final Adder adder) {
    SVNStatus status = getStatus(file);

    if (status != null &&
        (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_ADDED) ||
         SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_REPLACED))) {
      // file should be added
      adder.add(file);
      file = file.getParentFile();
      if (file != null) {
        addParents(file, adder);
      }
    }
  }

  private SVNStatus getStatus(File file) {
    SVNStatus result = null;
    WorkingCopyFormat format = mySvnVcs.getWorkingCopyFormat(file);

    try {
      result = WorkingCopyFormat.ONE_DOT_EIGHT.equals(format) ? getStatusCommandLine(file) : getStatusSvnKit(file);
    }
    catch (SVNException e) {
      // do nothing
    }

    return result;
  }

  private SVNStatus getStatusSvnKit(File file) throws SVNException {
    return mySvnVcs.createStatusClient().doStatus(file, false);
  }

  private SVNStatus getStatusCommandLine(File file) throws SVNException {
    return new SvnCommandLineStatusClient(mySvnVcs).doStatus(file, false);
  }

  private static List<File> collectPaths(final List<Change> changes) {
    // case sensitive..
    ArrayList<File> result = new ArrayList<File>();

    final Set<String> pathesSet = new HashSet<String>();
    for (Change change : changes) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision != null) {
        pathesSet.add(beforeRevision.getFile().getIOFile().getAbsolutePath());
      }
      if (afterRevision != null) {
        pathesSet.add(afterRevision.getFile().getIOFile().getAbsolutePath());
      }
    }

    for (String s : pathesSet) {
      result.add(new File(s));
    }
    return result;
  }

  public String getCheckinOperationName() {
    return SvnBundle.message("checkin.operation.name");
  }

  public List<VcsException> commit(List<Change> changes,
                                   String preparedComment,
                                   @NotNull NullableFunction<Object, Object> parametersHolder,
                                   Set<String> feedback) {
    return commitInt(collectPaths(changes), preparedComment, true, false, feedback);
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    return commit(changes, preparedComment, FunctionUtil.<Object, Object>nullConstant(), null);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> filePaths) {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    List<File> files = ChangesUtil.filePathsToFiles(filePaths);

    for (File file : files) {
      try {
        mySvnVcs.getFactory(file).createDeleteClient().delete(file, true, false, null);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }

    return exceptions;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    return scheduleUnversionedFilesForAddition(mySvnVcs, files);
  }

  public static List<VcsException> scheduleUnversionedFilesForAddition(@NotNull SvnVcs vcs, List<VirtualFile> files) {
    return scheduleUnversionedFilesForAddition(vcs, files, false);
  }

  public static List<VcsException> scheduleUnversionedFilesForAddition(@NotNull SvnVcs vcs, List<VirtualFile> files, final boolean recursive) {
    Collections.sort(files, FilePathComparator.getInstance());

    ISVNEventHandler eventHandler = new ISVNEventHandler() {
      @Override
      public void handleEvent(SVNEvent event, double progress) throws SVNException {
        final ProgressManager pm = ProgressManager.getInstance();
        final ProgressIndicator pi = pm.getProgressIndicator();
        // TODO: pi is null here when invoking "Add" action
        if (pi != null && event.getFile() != null) {
          File file = event.getFile();
          pi.setText(SvnBundle.message("progress.text2.adding", file.getName() + " (" + file.getParent() + ")"));
        }
      }

      @Override
      public void checkCancelled() throws SVNCancelException {
        final ProgressManager pm = ProgressManager.getInstance();
        final ProgressIndicator pi = pm.getProgressIndicator();
        if (pi != null) {
          if (pi.isCanceled()) throw new SVNCancelException();
        }
      }
    };

    List<VcsException> exceptions = new ArrayList<VcsException>();

    for (VirtualFile file : files) {
      try {
        File convertedFile = new File(FileUtil.toSystemDependentName(file.getPath()));
        SVNDepth depth = recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY;

        vcs.getFactory(convertedFile).createAddClient().add(convertedFile, depth, true, false, true, eventHandler);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }

    return exceptions;
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }

  private class KeepLocksComponent implements RefreshableOnComponent {
    private final JCheckBox myKeepLocksBox;
    private boolean myIsKeepLocks;
    private final JPanel myPanel;
    private final JCheckBox myAutoUpdate;

    public KeepLocksComponent(final Refreshable panel) {

      myPanel = new JPanel(new BorderLayout());
      myKeepLocksBox = new JCheckBox(SvnBundle.message("checkbox.chckin.keep.files.locked"));
      myKeepLocksBox.setSelected(myIsKeepLocks);
      myAutoUpdate = new JCheckBox("Auto-update after commit");

      myPanel.add(myAutoUpdate, BorderLayout.NORTH);
      myPanel.add(myKeepLocksBox, BorderLayout.CENTER);
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public boolean isKeepLocks() {
      return myKeepLocksBox != null && myKeepLocksBox.isSelected();
    }

    public boolean isAutoUpdate() {
      return myAutoUpdate != null && myAutoUpdate.isSelected();
    }

    public void refresh() {
    }

    public void saveState() {
      final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
      configuration.setKeepLocks(isKeepLocks());
      configuration.setAutoUpdateAfterCommit(isAutoUpdate());
    }

    public void restoreState() {
      final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
      myIsKeepLocks = configuration.isKeepLocks();
      myAutoUpdate.setSelected(configuration.isAutoUpdateAfterCommit());
    }
  }

}
