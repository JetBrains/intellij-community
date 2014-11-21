/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.properties.PropertiesMap;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

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

    final Collection<List<Change>> collections = SvnUtil.splitChangesIntoWc(mySvnVcs, changes);
    for (List<Change> collection : collections) {
      // to be more sure about nested changes, being or being not reverted
      final List<Change> innerChanges = new ArrayList<Change>(collection);
      Collections.sort(innerChanges, ChangesAfterPathComparator.getInstance());
      //for (Change change : innerChanges) {
        rollbackGroupForWc(innerChanges, exceptions, listener);
      //}
    }
  }

  private void rollbackGroupForWc(@NotNull List<Change> changes,
                                  @NotNull final List<VcsException> exceptions,
                                  @NotNull final RollbackProgressListener listener) {
    final UnversionedAndNotTouchedFilesGroupCollector collector = new UnversionedAndNotTouchedFilesGroupCollector();
    final ChangesChecker checker = new ChangesChecker(mySvnVcs, collector);

    checker.gather(changes);
    exceptions.addAll(checker.getExceptions());

    ProgressTracker revertHandler = new ProgressTracker() {
      public void consume(ProgressEvent event) {
        if (event.getAction() == EventAction.REVERT) {
          final File file = event.getFile();
          if (file != null) {
            listener.accept(file);
          }
        }
        if (event.getAction() == EventAction.FAILED_REVERT) {
          exceptions.add(new VcsException("Revert failed"));
        }
      }

      public void checkCancelled() {
        listener.checkCanceled();
      }
    };

    final List<CopiedAsideInfo> fromToModified = new ArrayList<CopiedAsideInfo>();
    final Map<File, PropertiesMap> properties = ContainerUtil.newHashMap();
    moveRenamesToTmp(exceptions, fromToModified, properties, collector);
    // adds (deletes)
    // deletes (adds)
    // modifications
    final Reverter reverter = new Reverter(mySvnVcs, revertHandler, exceptions);
    reverter.revert(checker.getForAdds(), true);
    reverter.revert(checker.getForDeletes(), true);
    reverter.revert(checker.getForEdits(), false);

    moveGroup(exceptions, fromToModified, properties);

    final List<Couple<File>> toBeDeleted = collector.getToBeDeleted();
    for (Couple<File> pair : toBeDeleted) {
      if (pair.getFirst().exists()) {
        FileUtil.delete(pair.getSecond());
      }
    }
  }

  private void moveRenamesToTmp(List<VcsException> exceptions,
                                List<CopiedAsideInfo> fromToModified,
                                final Map<File, PropertiesMap> properties,
                                final UnversionedAndNotTouchedFilesGroupCollector collector) {
    final Map<File, ThroughRenameInfo> fromTo = collector.getFromTo();
    try {
      final File tmp = FileUtil.createTempDirectory("forRename", "");
      final PropertyConsumer handler = new PropertyConsumer() {
        @Override
        public void handleProperty(File path, PropertyData property) throws SVNException {
          final ThroughRenameInfo info = collector.findToFile(new FilePathImpl(path, path.isDirectory()), null);
          if (info != null) {
            if (!properties.containsKey(info.getTo())) {
              properties.put(info.getTo(), new PropertiesMap());
            }
            properties.get(info.getTo()).put(property.getName(), property.getValue());
          }
        }

        @Override
        public void handleProperty(SVNURL url, PropertyData property) throws SVNException {
        }

        @Override
        public void handleProperty(long revision, PropertyData property) throws SVNException {
        }
      };

      // copy also directories here - for moving with svn
      // also, maybe still use just patching? -> well-tested thing, only deletion of folders might suffer
      // todo: special case: addition + move. mark it
      for (Map.Entry<File, ThroughRenameInfo> entry : fromTo.entrySet()) {
        final File source = entry.getKey();
        final ThroughRenameInfo info = entry.getValue();
        if (info.isVersioned()) {
          mySvnVcs.getFactory(source).createPropertyClient().list(SvnTarget.fromFile(source), SVNRevision.WORKING, Depth.EMPTY, handler);
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
    catch(VcsException e) {
      exceptions.add(e);
    }
  }

  private void moveGroup(final List<VcsException> exceptions,
                         List<CopiedAsideInfo> fromTo,
                         Map<File, PropertiesMap> properties) {
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
        catch (VcsException e) {
          exceptions.add(e);
        }
      }
    }

    applyProperties(properties, exceptions);
  }

  private void applyProperties(Map<File, PropertiesMap> propertiesMap, final List<VcsException> exceptions) {
    for (Map.Entry<File, PropertiesMap> entry : propertiesMap.entrySet()) {
      File file = entry.getKey();
      try {
        mySvnVcs.getFactory(file).createPropertyClient().setProperties(file, entry.getValue());
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }
  }

  private static class Reverter {
    @NotNull private final SvnVcs myVcs;
    private ProgressTracker myHandler;
    private final List<VcsException> myExceptions;

    private Reverter(@NotNull SvnVcs vcs, ProgressTracker handler, List<VcsException> exceptions) {
      myVcs = vcs;
      myHandler = handler;
      myExceptions = exceptions;
    }

    public void revert(@NotNull Collection<File> files, boolean recursive) {
      if (files.isEmpty()) return;

      File target = files.iterator().next();
      try {
        // Files passed here are split into groups by root and working copy format - thus we could determine factory based on first file
        myVcs.getFactory(target).createRevertClient()
          .revert(ArrayUtil.toObjectArray(files, File.class), Depth.allOrEmpty(recursive), myHandler);
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
    Info info = mySvnVcs.getInfo(file);
    if (info != null) {
      if (info.isFile()) {
        doRevert(file, false);
      } else {
        if (Info.SCHEDULE_ADD.equals(info.getSchedule())) {
          doRevert(file, true);
        } else {
          boolean is17OrGreater = is17OrGreaterCopy(file, info);
          if (is17OrGreater) {
            doRevert(file, true);
          } else {
            // do update to restore missing directory.
            mySvnVcs.getSvnKitManager().createUpdateClient().doUpdate(file, SVNRevision.HEAD, true);
          }
        }
      }
    } else {
      throw new VcsException("Can not get 'svn info' for " + file.getPath());
    }
  }

  private void doRevert(@NotNull File path, boolean recursive) throws VcsException {
    mySvnVcs.getFactory(path).createRevertClient().revert(new File[]{path}, Depth.allOrFiles(recursive), null);
  }

  private boolean is17OrGreaterCopy(final File file, final Info info) throws VcsException {
    final RootsToWorkingCopies copies = mySvnVcs.getRootsToWorkingCopies();
    WorkingCopy copy = copies.getMatchingCopy(info.getURL());

    if (copy == null) {
      WorkingCopyFormat format = mySvnVcs.getWorkingCopyFormat(file);

      return format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN);
    } else {
      return copy.is17Copy();
    }
  }

  private static class UnversionedAndNotTouchedFilesGroupCollector extends EmptyChangelistBuilder {
    private final List<Couple<File>> myToBeDeleted;
    private final Map<File, ThroughRenameInfo> myFromTo;
    // created by changes
    private TreeMap<String, File> myRenames;
    private Set<String> myAlsoReverted;

    private UnversionedAndNotTouchedFilesGroupCollector() {
      myFromTo = new HashMap<File, ThroughRenameInfo>();
      myToBeDeleted = new ArrayList<Couple<File>>();
    }

    @Override
    public void processUnversionedFile(final VirtualFile file) {
      toFromTo(file);
    }

    private void markRename(@NotNull final File beforeFile, @NotNull final File afterFile) {
      myToBeDeleted.add(Couple.of(beforeFile, afterFile));
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

    public List<Couple<File>> getToBeDeleted() {
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
  private static class SuperfluousRemover {
    private final Set<File> myParentPaths;
    private final boolean myCheckBefore;

    private SuperfluousRemover(boolean checkBefore) {
      myCheckBefore = checkBefore;
      myParentPaths = new HashSet<File>();
    }

    protected boolean accept(@NotNull Change change) {
      ContentRevision mainRevision = myCheckBefore ? change.getBeforeRevision() : change.getAfterRevision();
      ContentRevision otherRevision = !myCheckBefore ? change.getBeforeRevision() : change.getAfterRevision();

      if (mainRevision == null || MoveRenameReplaceCheck.check(change)) {
        check(otherRevision.getFile().getIOFile());
        return true;
      }

      return false;
    }

    public void check(@NotNull final File file) {
      boolean parentAlreadyRegistered = ContainerUtil.or(myParentPaths, new Condition<File>() {
        @Override
        public boolean value(@NotNull File parentCandidate) {
          return VfsUtilCore.isAncestor(parentCandidate, file, true);
        }
      });

      if (!parentAlreadyRegistered) {
        ContainerUtil.retainAll(myParentPaths, new Condition<File>() {
          @Override
          public boolean value(@NotNull File childCandidate) {
            return !VfsUtilCore.isAncestor(file, childCandidate, true);
          }
        });

        myParentPaths.add(file);
      }
    }

    public Set<File> getParentPaths() {
      return myParentPaths;
    }
  }

  private static class ChangesChecker {
    @NotNull private final SuperfluousRemover myForAdds;
    @NotNull private final SuperfluousRemover myForDeletes;
    private final List<File> myForEdits;

    private final SvnChangeProvider myChangeProvider;
    private final UnversionedAndNotTouchedFilesGroupCollector myCollector;

    private final List<VcsException> myExceptions;

    private ChangesChecker(@NotNull SvnVcs vcs, UnversionedAndNotTouchedFilesGroupCollector collector) {
      myChangeProvider = (SvnChangeProvider)vcs.getChangeProvider();
      myCollector = collector;
      myForAdds = new SuperfluousRemover(true);
      myForDeletes = new SuperfluousRemover(false);
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
          catch (SvnBindException e) {
            myExceptions.add(e);
          }
        }
      }

      for (Change change : changes) {
        final ContentRevision afterRevision = change.getAfterRevision();

        boolean checked = myForAdds.accept(change);
        checked |= myForDeletes.accept(change);

        if (! checked) {
          myForEdits.add(afterRevision.getFile().getIOFile());
        }
      }
    }

    @NotNull
    public Collection<File> getForAdds() {
      return myForAdds.getParentPaths();
    }

    @NotNull
    public Collection<File> getForDeletes() {
      return myForDeletes.getParentPaths();
    }

    public List<VcsException> getExceptions() {
      return myExceptions;
    }

    @NotNull
    public List<File> getForEdits() {
      return myForEdits;
    }
  }
}
