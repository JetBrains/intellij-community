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
package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.revert.RevertClient;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class SvnRollbackEnvironment extends DefaultRollbackEnvironment {
  private final SvnVcs mySvnVcs;

  public SvnRollbackEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  @Override
  public String getRollbackOperationName() {
    return SvnBundle.message("action.name.revert");
  }

  public void rollbackChanges(List<Change> changes, final List<VcsException> exceptions, @NotNull final RollbackProgressListener listener) {
    listener.indeterminate();
    final SvnChangeProvider changeProvider = (SvnChangeProvider) mySvnVcs.getChangeProvider();
    final Collection<List<Change>> collections = SvnUtil.splitChangesIntoWc(mySvnVcs, changes);
    for (List<Change> collection : collections) {
      // to be more sure about nested changes, being or being not reverted
      final List<Change> innerChanges = new ArrayList<Change>(collection);
      Collections.sort(innerChanges, ChangesAfterPathComparator.getInstance());
      //for (Change change : innerChanges) {
        rollbackGroupForWc(innerChanges, exceptions, listener, changeProvider);
      //}
    }
  }

  private void rollbackGroupForWc(List<Change> changes,
                                  final List<VcsException> exceptions,
                                  final RollbackProgressListener listener,
                                  SvnChangeProvider changeProvider) {
    final UnversionedAndNotTouchedFilesGroupCollector collector = new UnversionedAndNotTouchedFilesGroupCollector();

    final ChangesChecker checker = new ChangesChecker(changeProvider, collector);
    checker.gather(changes);
    exceptions.addAll(checker.getExceptions());

    ISVNEventHandler revertHandler = new ISVNEventHandler() {
      public void handleEvent(SVNEvent event, double progress) {
        if (event.getAction() == SVNEventAction.REVERT) {
          final File file = event.getFile();
          if (file != null) {
            listener.accept(file);
          }
        }
        if (event.getAction() == SVNEventAction.FAILED_REVERT) {
          exceptions.add(new VcsException("Revert failed"));
        }
      }

      public void checkCancelled() {
        listener.checkCanceled();
      }
    };

    final List<CopiedAsideInfo> fromToModified = new ArrayList<CopiedAsideInfo>();
    final MultiMap<File, SVNPropertyData> properties = new MultiMap<File, SVNPropertyData>();
    moveRenamesToTmp(exceptions, fromToModified, properties, collector);
    // adds (deletes)
    // deletes (adds)
    // modifications
    final Reverter reverter = new Reverter(mySvnVcs.getFactory().createRevertClient(), revertHandler, exceptions);
    reverter.revert(checker.getForAdds(), true);
    reverter.revert(checker.getForDeletes(), true);
    final List<File> edits = checker.getForEdits();
    reverter.revert(edits.toArray(new File[edits.size()]), false);

    moveGroup(exceptions, fromToModified, properties);

    final List<Pair<File, File>> toBeDeleted = collector.getToBeDeleted();
    for (Pair<File, File> pair : toBeDeleted) {
      if (pair.getFirst().exists()) {
        FileUtil.delete(pair.getSecond());
      }
    }
  }

  private void moveRenamesToTmp(List<VcsException> exceptions,
                                List<CopiedAsideInfo> fromToModified,
                                final MultiMap<File, SVNPropertyData> properties,
                                final UnversionedAndNotTouchedFilesGroupCollector collector) {
    final Map<File, ThroughRenameInfo> fromTo = collector.getFromTo();
    try {
      final File tmp = FileUtil.createTempDirectory("forRename", "");
      final SVNWCClient client = mySvnVcs.createWCClient();
      final ISVNPropertyHandler handler = new ISVNPropertyHandler() {
        @Override
        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
          final ThroughRenameInfo info = collector.findToFile(new FilePathImpl(path, path.isDirectory()), null);
          if (info != null) {
            properties.putValue(info.getTo(), property);
          }
        }

        @Override
        public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        }

        @Override
        public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        }
      };

      // copy also directories here - for moving with svn
      // also, maybe still use just patching? -> well-tested thing, only deletion of folders might suffer
      // todo: special case: addition + move. mark it
      for (Map.Entry<File, ThroughRenameInfo> entry : fromTo.entrySet()) {
        final File source = entry.getKey();
        final ThroughRenameInfo info = entry.getValue();
        if (info.isVersioned()) {
          client.doGetProperty(source, null, SVNRevision.UNDEFINED, SVNRevision.WORKING, SVNDepth.EMPTY, handler, null);
        }
        if (source.isDirectory()) {
          if (! FileUtil.filesEqual(info.getTo(), info.getFirstTo())) {
            fromToModified.add(new CopiedAsideInfo(info.getParentImmediateReverted(), info.getTo(), info.getFirstTo(), null));
          }
          continue;
        }
        final File tmpFile = FileUtil.createTempFile(tmp, source.getName(), "", false);
        tmpFile.mkdirs();
        FileUtil.delete(tmpFile);
        FileUtil.copy(source, tmpFile);
        fromToModified.add(new CopiedAsideInfo(info.getParentImmediateReverted(), info.getTo(), info.getFirstTo(), tmpFile));
      }
    }
    catch (IOException e) {
      exceptions.add(new VcsException(e));
    }
    catch (SVNException e) {
      exceptions.add(new VcsException(e));
    }
  }

  private void moveGroup(final List<VcsException> exceptions,
                         List<CopiedAsideInfo> fromTo,
                         MultiMap<File, SVNPropertyData> properties) {
    Collections.sort(fromTo, new Comparator<CopiedAsideInfo>() {
      @Override
      public int compare(CopiedAsideInfo o1, CopiedAsideInfo o2) {
        return FileUtil.compareFiles(o1.getTo(), o2.getTo());
      }
    });
    for (CopiedAsideInfo info : fromTo) {
      if (info.getParentImmediateReverted().exists()) {
        // parent successfully renamed/moved
        try {
          final File from = info.getFrom();
          final File target = info.getTo();
          if (from != null && ! FileUtil.filesEqual(from, target) && ! target.exists()) {
            SvnFileSystemListener.moveFileWithSvn(mySvnVcs, from, target);
          }
          final File root = info.getTmpPlace();
          if (root == null) continue;
          if (! root.isDirectory()) {
            if (target.exists()) {
              FileUtil.copy(root, target);
            } else {
              FileUtil.rename(root, target);
            }
          } else {
            FileUtil.processFilesRecursively(root, new Processor<File>() {
              @Override
              public boolean process(File file) {
                if (file.isDirectory()) return true;
                String relativePath = FileUtil.getRelativePath(root.getPath(), file.getPath(), File.separatorChar);
                File newFile = new File(target, relativePath);
                newFile.getParentFile().mkdirs();
                try {
                  if (target.exists()) {
                    FileUtil.copy(file, newFile);
                  } else {
                    FileUtil.rename(file, newFile);
                  }
                }
                catch (IOException e) {
                  exceptions.add(new VcsException(e));
                }
                return true;
              }
            });
          }
        }
        catch (IOException e) {
          exceptions.add(new VcsException(e));
        }
        catch (SVNException e) {
          exceptions.add(new VcsException(e));
        }
      }
    }

    applyProperties(properties, exceptions);
  }

  private void applyProperties(MultiMap<File, SVNPropertyData> propertiesMap, final List<VcsException> exceptions) {
    final SVNWCClient client = mySvnVcs.createWCClient();
    for (Map.Entry<File, Collection<SVNPropertyData>> entry : propertiesMap.entrySet()) {
      final File file = entry.getKey();
      final Collection<SVNPropertyData> propertyDatas = entry.getValue();
        try {
          client.doSetProperty(file, new ISVNPropertyValueProvider() {
            @Override
            public SVNProperties providePropertyValues(File path, SVNProperties properties) throws SVNException {
              final SVNProperties result = new SVNProperties();
              for (SVNPropertyData data : propertyDatas) {
                result.put(data.getName(), data.getValue());
              }
              return result;
            }
          }, true, SVNDepth.EMPTY, null, null);
        }
        catch (SVNException e) {
          exceptions.add(new VcsException(e));
        }
    }
  }

  private static class Reverter {
    private final RevertClient myClient;
    private ISVNEventHandler myHandler;
    private final List<VcsException> myExceptions;

    private Reverter(RevertClient client, ISVNEventHandler handler, List<VcsException> exceptions) {
      myClient = client;
      myHandler = handler;
      myExceptions = exceptions;
    }

    public void revert(final File[] files, final boolean recursive) {
      if (files.length == 0) return;
      try {
        myClient.revert(files, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, myHandler);
      }
      catch (VcsException e) {
        processRevertError(e);
      }
    }

    private void processRevertError(@NotNull VcsException e) {
      if (e.getCause() instanceof  SVNException) {
        SVNException cause = (SVNException)e.getCause();

        // skip errors on unversioned resources.
        if (cause.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
          myExceptions.add(e);
        }
      } else {
        myExceptions.add(e);
      }
    }
  }

  public void rollbackMissingFileDeletion(List<FilePath> filePaths, final List<VcsException> exceptions,
                                                        final RollbackProgressListener listener) {
    List<File> files = ChangesUtil.filePathsToFiles(filePaths);
    for (File file : files) {
      listener.accept(file);
      try {
        revertFileOrDir(file);
      } catch (VcsException e) {
        exceptions.add(e);
      } catch (SVNException e) {
        exceptions.add(new VcsException(e));
      }
    }
  }

  private void revertFileOrDir(File file) throws SVNException, VcsException {
    SVNInfo info = mySvnVcs.getInfo(file);
    if (info != null) {
      if (info.getKind() == SVNNodeKind.FILE) {
        doRevert(file, false);
      } else {
        if (SVNProperty.SCHEDULE_ADD.equals(info.getSchedule())) {
          doRevert(file, true);
        } else {
          boolean is17OrGreater = is17OrGreaterCopy(file, info);
          if (is17OrGreater) {
            doRevert(file, true);
          } else {
            // do update to restore missing directory.
            mySvnVcs.createUpdateClient().doUpdate(file, SVNRevision.HEAD, true);
          }
        }
      }
    } else {
      throw new VcsException("Can not get 'svn info' for " + file.getPath());
    }
  }

  private void doRevert(@NotNull File path, boolean recursive) throws VcsException {
    mySvnVcs.getFactory(path).createRevertClient().revert(new File[]{path}, SVNDepth.fromRecurse(recursive), null);
  }

  private boolean is17OrGreaterCopy(final File file, final SVNInfo info) throws VcsException {
    final RootsToWorkingCopies copies = mySvnVcs.getRootsToWorkingCopies();
    WorkingCopy copy = copies.getMatchingCopy(info.getURL());

    if (copy == null) {
      WorkingCopyFormat format = mySvnVcs.getWorkingCopyFormat(file);

      return !WorkingCopyFormat.UNKNOWN.equals(format) && format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN);
    } else {
      return copy.is17Copy();
    }
  }

  private static class UnversionedAndNotTouchedFilesGroupCollector extends EmptyChangelistBuilder {
    private final List<Pair<File, File>> myToBeDeleted;
    private final Map<File, ThroughRenameInfo> myFromTo;
    // created by changes
    private TreeMap<String, File> myRenames;
    private Set<String> myAlsoReverted;

    private UnversionedAndNotTouchedFilesGroupCollector() {
      myFromTo = new HashMap<File, ThroughRenameInfo>();
      myToBeDeleted = new ArrayList<Pair<File, File>>();
    }

    @Override
    public void processUnversionedFile(final VirtualFile file) {
      toFromTo(file);
    }

    private void markRename(@NotNull final File beforeFile, @NotNull final File afterFile) {
      myToBeDeleted.add(new Pair<File, File>(beforeFile, afterFile));
    }

    public ThroughRenameInfo findToFile(@NotNull final FilePath file, @Nullable final File firstTo) {
      final String path = FilePathsHelper.convertPath(file);
      if (myAlsoReverted.contains(path)) return null;
      final NavigableMap<String, File> head = myRenames.headMap(path, true);
      if (head == null || head.isEmpty()) return null;
      for (Map.Entry<String, File> entry : head.descendingMap().entrySet()) {
        if (path.equals(entry.getKey())) return null;
        if (path.startsWith(entry.getKey())) {
          final String convertedBase = FileUtil.toSystemIndependentName(entry.getKey());
          final String convertedChild = FileUtil.toSystemIndependentName(file.getPath());
          final String relativePath = FileUtil.getRelativePath(convertedBase, convertedChild, '/');
          assert relativePath != null;
          return new ThroughRenameInfo(entry.getValue(), new File(entry.getValue(), relativePath), firstTo, file.getIOFile(), firstTo != null);
        }
      }
      return null;
    }

    private void toFromTo(VirtualFile file) {
      final FilePathImpl path = new FilePathImpl(file);
      final ThroughRenameInfo info = findToFile(path, null);
      if (info != null) {
        myFromTo.put(path.getIOFile(), info);
      }
    }

    private void processChangeImpl(final Change change) {
      if (change.getAfterRevision() != null) {
        final FilePath after = change.getAfterRevision().getFile();
        final ThroughRenameInfo info = findToFile(after, change.getBeforeRevision() == null ? null : change.getBeforeRevision().getFile().getIOFile());
        if (info != null) {
          myFromTo.put(after.getIOFile(), info);
        }
      }
    }

    @Override
    public void processChange(Change change, VcsKey vcsKey) {
      processChangeImpl(change);
    }

    @Override
    public void processChangeInList(Change change, @Nullable ChangeList changeList, VcsKey vcsKey) {
      processChangeImpl(change);
    }

    @Override
    public void processChangeInList(Change change, String changeListName, VcsKey vcsKey) {
      processChangeImpl(change);
    }

    @Override
    public void processIgnoredFile(VirtualFile file) {
      // as with unversioned
      toFromTo(file);
    }

    public List<Pair<File, File>> getToBeDeleted() {
      return myToBeDeleted;
    }

    public Map<File, ThroughRenameInfo> getFromTo() {
      return myFromTo;
    }

    public void setRenamesMap(TreeMap<String, File> renames) {
      myRenames = renames;
    }

    public void setAlsoReverted(Set<String> alsoReverted) {
      myAlsoReverted = alsoReverted;
    }
  }

  private static class CopiedAsideInfo {
    private final File myParentImmediateReverted;
    private final File myTo;
    private final File myFrom;
    private final File myTmpPlace;

    private CopiedAsideInfo(File parentImmediateReverted, File to, File from, File tmpPlace) {
      myParentImmediateReverted = parentImmediateReverted;
      myTo = to;
      myFrom = from;
      myTmpPlace = tmpPlace;
    }

    public File getParentImmediateReverted() {
      return myParentImmediateReverted;
    }

    public File getTo() {
      return myTo;
    }

    public File getFrom() {
      return myFrom;
    }

    public File getTmpPlace() {
      return myTmpPlace;
    }

    @Override
    public String toString() {
      return myFrom + " -> " + myTo;
    }
  }

  private static class ThroughRenameInfo {
    private final File myParentImmediateReverted;
    private final File myTo;
    private final File myFirstTo;
    private final File myFrom;
    private final boolean myVersioned;

    private ThroughRenameInfo(File parentImmediateReverted, File to, File firstTo, File from, boolean versioned) {
      myParentImmediateReverted = parentImmediateReverted;
      myTo = to;
      myFrom = from;
      myVersioned = versioned;
      myFirstTo = firstTo;
    }

    public File getFirstTo() {
      return myFirstTo;
    }

    public boolean isVersioned() {
      return myVersioned;
    }

    public File getParentImmediateReverted() {
      return myParentImmediateReverted;
    }

    public File getTo() {
      return myTo;
    }

    public File getFrom() {
      return myFrom;
    }
  }

  // both adds and deletes
  private static abstract class SuperfluousRemover {
    private final Set<File> myParentPaths;

    private SuperfluousRemover() {
      myParentPaths = new HashSet<File>();
    }

    @Nullable
    protected abstract File accept(final Change change);

    public void check(final File file) {
      for (Iterator<File> iterator = myParentPaths.iterator(); iterator.hasNext();) {
        final File parentPath = iterator.next();
        if (VfsUtil.isAncestor(parentPath, file, true)) {
          return;
        } else if (VfsUtil.isAncestor(file, parentPath, true)) {
          iterator.remove();
          // remove others; dont check for 1st variant any more
          for (; iterator.hasNext();) {
            final File innerParentPath = iterator.next();
            if (VfsUtil.isAncestor(file, innerParentPath, true)) {
              iterator.remove();
            }
          }
          // will be added in the end
        }
      }
      myParentPaths.add(file);
    }

    public Set<File> getParentPaths() {
      return myParentPaths;
    }
  }

  private static class ChangesChecker {
    private final SuperfluousRemover myForAdds;
    private final SuperfluousRemover myForDeletes;
    private final List<File> myForEdits;

    private final SvnChangeProvider myChangeProvider;
    private final UnversionedAndNotTouchedFilesGroupCollector myCollector;

    private final List<VcsException> myExceptions;

    private ChangesChecker(SvnChangeProvider changeProvider, UnversionedAndNotTouchedFilesGroupCollector collector) {
      myChangeProvider = changeProvider;
      myCollector = collector;

      myForAdds = new SuperfluousRemover() {
        @Nullable
        @Override
        protected File accept(Change change) {
          final ContentRevision beforeRevision = change.getBeforeRevision();
          final ContentRevision afterRevision = change.getAfterRevision();
          if (beforeRevision == null || MoveRenameReplaceCheck.check(change)) {
            return afterRevision.getFile().getIOFile();
          }
          return null;
        }
      };

      myForDeletes = new SuperfluousRemover() {
        @Nullable
        @Override
        protected File accept(Change change) {
          final ContentRevision beforeRevision = change.getBeforeRevision();
          final ContentRevision afterRevision = change.getAfterRevision();
          if (afterRevision == null || MoveRenameReplaceCheck.check(change)) {
            return beforeRevision.getFile().getIOFile();
          }
          return null;
        }
      };

      myForEdits = new ArrayList<File>();
      myExceptions = new ArrayList<VcsException>();
    }

    public void gather(final List<Change> changes) {
      final TreeMap<String, File> renames = new TreeMap<String, File>();
      final Set<String> alsoReverted = new HashSet<String>();
      final Map<String, FilePath> files = new HashMap<String, FilePath>();
      for (Change change : changes) {
        final ContentRevision beforeRevision = change.getBeforeRevision();
        final ContentRevision afterRevision = change.getAfterRevision();
        final String key = afterRevision == null ? null : FilePathsHelper.convertWithLastSeparator(afterRevision.getFile());
        if (MoveRenameReplaceCheck.check(change)) {
          final File beforeFile = beforeRevision.getFile().getIOFile();
          renames.put(key, beforeFile);
          files.put(key, afterRevision.getFile());
          myCollector.markRename(beforeFile, afterRevision.getFile().getIOFile());
        } else if (afterRevision != null) {
          alsoReverted.add(key);
        }
      }
      if (! renames.isEmpty()) {
        final ArrayList<String> paths = new ArrayList<String>(renames.keySet());
        if (paths.size() > 1) {
          FilterFilePathStrings.getInstance().doFilter(paths);
        }
        myCollector.setRenamesMap(renames);
        myCollector.setAlsoReverted(alsoReverted);
        for (String path : paths) {
          try {
            myChangeProvider.getChanges(files.get(path), true, myCollector);
          }
          catch (SVNException e) {
            myExceptions.add(new VcsException(e));
          }
        }
      }

      for (Change change : changes) {
        final ContentRevision afterRevision = change.getAfterRevision();

        boolean checked = getAddDelete(myForAdds, change);
        checked |= getAddDelete(myForDeletes, change);

        if (! checked) {
          myForEdits.add(afterRevision.getFile().getIOFile());
        }
      }
    }

    private boolean getAddDelete(final SuperfluousRemover superfluousRemover, final Change change) {
      final File file = superfluousRemover.accept(change);
      if (file != null) {
        superfluousRemover.check(file);
        return true;
      }
      return false;
    }

    public File[] getForAdds() {
      return convert(myForAdds.getParentPaths());
    }

    public File[] getForDeletes() {
      return convert(myForDeletes.getParentPaths());
    }

    private File[] convert(final Collection<File> paths) {
      return paths.toArray(new File[paths.size()]);
    }

    public List<VcsException> getExceptions() {
      return myExceptions;
    }

    public List<File> getForEdits() {
      return myForEdits;
    }
  }
}
