package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.*;

/**
 * @author max
 * @author yole
 */
public class SvnChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangeProvider");
  public static final String ourDefaultListName = VcsBundle.message("changes.default.changlist.name");

  private final SvnVcs myVcs;
  private final VcsContextFactory myFactory;
  private final SvnBranchConfigurationManager myBranchConfigurationManager;
  private final ExcludedFileIndex myExcludedFileIndex;
  private final ChangeListManager myClManager;
  private final SvnFileUrlMapping myMapping;

  public SvnChangeProvider(final SvnVcs vcs) {
    myVcs = vcs;
    myFactory = VcsContextFactory.SERVICE.getInstance();
    myBranchConfigurationManager = SvnBranchConfigurationManager.getInstance(myVcs.getProject());
    myExcludedFileIndex = ExcludedFileIndex.getInstance(myVcs.getProject());
    myClManager = ChangeListManager.getInstance(myVcs.getProject());
    myMapping = myVcs.getSvnFileUrlMapping();
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, ProgressIndicator progress,
                         final ChangeListManagerGate addGate) throws VcsException {
    final SvnScopeZipper zipper = new SvnScopeZipper(dirtyScope);
    zipper.run();

    final Map<String, SvnScopeZipper.MyDirNonRecursive> nonRecursiveMap = zipper.getNonRecursiveDirs();
    // translate into terms of File.getAbsolutePath()
    final Map<String, Map> preparedMap = new HashMap<String, Map>();
    for (SvnScopeZipper.MyDirNonRecursive item : nonRecursiveMap.values()) {
      final Map result = new HashMap();
      for (FilePath path : item.getChildrenList()) {
        result.put(path.getName(), path.getIOFile());
      }
      preparedMap.put(item.getDir().getIOFile().getAbsolutePath(), result);
    }
    final ISVNStatusFileProvider fileProvider = new ISVNStatusFileProvider() {
      public Map getChildrenFiles(File parent) {
        return preparedMap.get(parent.getAbsolutePath());
      }
    };

    try {
      final SvnChangeProviderContext context = new SvnChangeProviderContext(myVcs, builder, progress);

      for (FilePath path : zipper.getRecursiveDirs()) {
        processFile(path, context, SVNDepth.INFINITY, context.getClient());
      }

      context.getClient().setFilesProvider(fileProvider);
      for (SvnScopeZipper.MyDirNonRecursive item : nonRecursiveMap.values()) {
        processFile(item.getDir(), context, SVNDepth.FILES, context.getClient());
      }

      // they are taken under non recursive: ENTRIES file is read anyway, so we get to know parent status also for free
      /*for (FilePath path : zipper.getSingleFiles()) {
        FileStatus status = getParentStatus(context, path);
        processFile(path, context, status, false, context.getClient());
      }*/

      processCopiedAndDeleted(context);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private void processCopiedAndDeleted(final SvnChangeProviderContext context) throws SVNException {
    for(SvnChangedFile copiedFile: context.getCopiedFiles()) {
      if (context.isCanceled()) {
        throw new ProcessCanceledException();
      }
      processCopiedFile(copiedFile, context.getBuilder(), context);
    }
    for(SvnChangedFile deletedFile: context.getDeletedFiles()) {
      if (context.isCanceled()) {
        throw new ProcessCanceledException();
      }
      processStatus(deletedFile.getFilePath(), deletedFile.getStatus(), context);
    }
  }

  public void getChanges(final FilePath path, final boolean recursive, final ChangelistBuilder builder) throws SVNException {
    final SvnChangeProviderContext context = new SvnChangeProviderContext(myVcs, builder, null);
    processFile(path, context, recursive ? SVNDepth.INFINITY : SVNDepth.IMMEDIATES, context.getClient());
    processCopiedAndDeleted(context);
  }

  @Nullable
  private String changeListNameFromStatus(final SVNStatus status) {
    if (WorkingCopyFormat.getInstance(status.getWorkingCopyFormat()).supportsChangelists()) {
      if (SVNNodeKind.FILE.equals(status.getKind())) {
        final String clName = status.getChangelistName();
        return (clName == null) ? null : clName;
      }
    }
    // always null for earlier versions
    return null;
  }

  private void processCopiedFile(SvnChangedFile copiedFile, ChangelistBuilder builder, SvnChangeProviderContext context) throws SVNException {
    boolean foundRename = false;
    final SVNStatus copiedStatus = copiedFile.getStatus();
    final String copyFromURL = copiedFile.getCopyFromURL();
    final FilePath copiedToPath = copiedFile.getFilePath();

    // if copy target is _deleted_, treat like deleted, not moved!
    /*for (Iterator<SvnChangedFile> iterator = context.getDeletedFiles().iterator(); iterator.hasNext();) {
      final SvnChangedFile deletedFile = iterator.next();
      final FilePath deletedPath = deletedFile.getFilePath();

      if (Comparing.equal(deletedPath, copiedToPath)) {
        return;
      }
    }*/

    final Set<SvnChangedFile> deletedToDelete = new HashSet<SvnChangedFile>();

    for (Iterator<SvnChangedFile> iterator = context.getDeletedFiles().iterator(); iterator.hasNext();) {
      SvnChangedFile deletedFile = iterator.next();
      final SVNStatus deletedStatus = deletedFile.getStatus();
      if ((deletedStatus != null) && (deletedStatus.getURL() != null) && Comparing.equal(copyFromURL, deletedStatus.getURL().toString())) {
        final String clName = changeListNameFromStatus(copiedFile.getStatus());
        builder.processChangeInList(createMovedChange(createBeforeRevision(deletedFile, true),
                                 CurrentContentRevision.create(copiedFile.getFilePath()), copiedStatus, deletedStatus, context), clName);
        deletedToDelete.add(deletedFile);
        for(Iterator<SvnChangedFile> iterChild = context.getDeletedFiles().iterator(); iterChild.hasNext();) {
          SvnChangedFile deletedChild = iterChild.next();
          final SVNStatus childStatus = deletedChild.getStatus();
          if (childStatus == null) {
            continue;
          }
          final SVNURL childUrl = childStatus.getURL();
          if (childUrl == null) {
            continue;
          }
          final String childURL = childUrl.toString();
          if (StringUtil.startsWithConcatenationOf(childURL, copyFromURL, "/")) {
            String relativePath = childURL.substring(copyFromURL.length());
            File newPath = new File(copiedFile.getFilePath().getIOFile(), relativePath);
            FilePath newFilePath = myFactory.createFilePathOn(newPath);
            if (!context.isDeleted(newFilePath)) {
              builder.processChangeInList(createMovedChange(createBeforeRevision(deletedChild, true),
                                                            CurrentContentRevision.create(newFilePath),
                                                            context.getTreeConflictStatus(newPath), childStatus, context), clName);
              deletedToDelete.add(deletedChild);
            }
          }
        }
        foundRename = true;
        break;
      }
    }

    final List<SvnChangedFile> deletedFiles = context.getDeletedFiles();
    for (SvnChangedFile file : deletedToDelete) {
      deletedFiles.remove(file);
    }

    // handle the case when the deleted file wasn't included in the dirty scope - try searching for the local copy
    // by building a relative url
    if (!foundRename && copiedStatus.getURL() != null) {
      File wcPath = guessWorkingCopyPath(copiedStatus.getFile(), copiedStatus.getURL(), copyFromURL);
      SVNStatus status;
      try {
        status = context.getClient().doStatus(wcPath, false);
      }
      catch(SVNException ex) {
        status = null;
      }
      if (status != null && status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
        final FilePath filePath = myFactory.createFilePathOnDeleted(wcPath, false);
        final SvnContentRevision beforeRevision = SvnContentRevision.create(myVcs, filePath, status.getCommittedRevision());
        final ContentRevision afterRevision = CurrentContentRevision.create(copiedFile.getFilePath());
        builder.processChangeInList(createMovedChange(beforeRevision, afterRevision, copiedStatus, status, context), changeListNameFromStatus(status));
        foundRename = true;
      }
    }

    if (!foundRename) {
      // for debug
      LOG.info("Rename not found for " + copiedFile.getFilePath().getPresentableUrl());
      processStatus(copiedFile.getFilePath(), copiedStatus, context);
    }
  }

  private SvnContentRevision createBeforeRevision(final SvnChangedFile changedFile, final boolean forDeleted) {
    return SvnContentRevision.create(myVcs,
        forDeleted ? FilePathImpl.createForDeletedFile(changedFile.getStatus().getFile(), changedFile.getFilePath().isDirectory()) :
                   changedFile.getFilePath(), changedFile.getStatus().getCommittedRevision());
  }

  private static File guessWorkingCopyPath(final File file, @NotNull final SVNURL url, final String copyFromURL) throws SVNException {
    String copiedPath = url.getPath();
    String copyFromPath = SVNURL.parseURIEncoded(copyFromURL).getPath();
    String commonPathAncestor = SVNPathUtil.getCommonPathAncestor(copiedPath, copyFromPath);
    int pathSegmentCount = SVNPathUtil.getSegmentsCount(copiedPath);
    int ancestorSegmentCount = SVNPathUtil.getSegmentsCount(commonPathAncestor);
    boolean startsWithSlash = file.getAbsolutePath().startsWith("/");
    List<String> segments = StringUtil.split(file.getPath(), File.separator);
    List<String> copyFromPathSegments = StringUtil.split(copyFromPath, "/");
    List<String> resultSegments = new ArrayList<String>();
    final int keepSegments = segments.size() - pathSegmentCount + ancestorSegmentCount;
    for(int i=0; i< keepSegments; i++) {
      resultSegments.add(segments.get(i));
    }
    for(int i=ancestorSegmentCount; i<copyFromPathSegments.size(); i++) {
      resultSegments.add(copyFromPathSegments.get(i));
    }

    String result = StringUtil.join(resultSegments, "/");
    if (startsWithSlash) {
      result = "/" + result;
    }
    return new File(result);
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  public void doCleanup(final List<VirtualFile> files) {
    new CleanupWorker(files.toArray(new VirtualFile[files.size()]), myVcs.getProject(), "action.Subversion.cleanup.progress.title").execute();
  }

  private void processFile(FilePath path, final SvnChangeProviderContext context, final SVNDepth depth, final SVNStatusClient statusClient) throws SVNException {
    if (context.isCanceled()) {
      throw new ProcessCanceledException();
    }
    try {
      if (path.isDirectory()) {
        statusClient.doStatus(path.getIOFile(), SVNRevision.WORKING, depth,
                              false, true, true, false, new ISVNStatusHandler() {
          public void handleStatus(SVNStatus status) throws SVNException {
            if (context.isCanceled()) {
              throw new ProcessCanceledException();
            }
            FilePath path = VcsUtil.getFilePath(status.getFile(), status.getKind().equals(SVNNodeKind.DIR));
            final VirtualFile vFile = path.getVirtualFile();
            if (vFile != null && myExcludedFileIndex.isExcludedFile(vFile)) {
              return;
            }
            processStatusFirstPass(path, status, context);
            if ((vFile != null) && (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) && path.isDirectory()) {
              if (myClManager.isIgnoredFile(vFile)) {
                // for directory, this means recursively ignored by Idea
                context.getBuilder().processIgnoredFile(vFile);
              } else {
                // process children of this file with another client.
                SVNStatusClient client = myVcs.createStatusClient();
                // todo take care, maybe also allow to query with other depth values
                if (SVNDepth.INFINITY.equals(depth) && path.isDirectory()) {
                  VirtualFile[] children = vFile.getChildren();
                  for (VirtualFile aChildren : children) {
                    FilePath filePath = VcsUtil.getFilePath(aChildren.getPath(), aChildren.isDirectory());
                    processFile(filePath, context, depth, client);
                  }
                }
              }
            }
          }
        }, null);
      } else {
        processFile(path, context);
      }
    } catch (SVNException e) {
      if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
        final VirtualFile virtualFile = path.getVirtualFile();
        if (virtualFile != null) {
          if (myExcludedFileIndex.isExcludedFile(virtualFile)) return;
          context.getBuilder().processUnversionedFile(virtualFile);
        }
        // process children recursively!
        if ((! SVNDepth.EMPTY.equals(depth)) && path.isDirectory() && virtualFile != null) {
          VirtualFile[] children = virtualFile.getChildren();
          for (VirtualFile child : children) {
            FilePath filePath = VcsUtil.getFilePath(child.getPath(), child.isDirectory());
            processFile(filePath, context, SVNDepth.INFINITY.equals(depth) ? SVNDepth.INFINITY : SVNDepth.EMPTY, statusClient);
          }
        }
      }
      else {
        throw e;
      }
    }
  }

  private void processFile(FilePath filePath, SvnChangeProviderContext context) throws SVNException {
    SVNStatus status = context.getClient().doStatus(filePath.getIOFile(), false, false);
    processStatusFirstPass(filePath, status, context);
  }

  private void processStatusFirstPass(final FilePath filePath, final SVNStatus status, final SvnChangeProviderContext context) throws SVNException {
    if (status == null) {
      // external to wc
      return;
    }
    if (status.getRemoteLock() != null) {
      final SVNLock lock = status.getRemoteLock();
      context.getBuilder().processLogicallyLockedFolder(filePath.getVirtualFile(),
            new LogicalLock(false, lock.getOwner(), lock.getComment(), lock.getCreationDate(), lock.getExpirationDate()));
    }
    if (status.getLocalLock() != null) {
      final SVNLock lock = status.getLocalLock();
      context.getBuilder().processLogicallyLockedFolder(filePath.getVirtualFile(),
            new LogicalLock(true, lock.getOwner(), lock.getComment(), lock.getCreationDate(), lock.getExpirationDate()));
    }
    if (filePath.isDirectory() && status.isLocked()) {
      context.getBuilder().processLockedFolder(filePath.getVirtualFile());
    }
    if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED && status.getCopyFromURL() != null) {
      context.addCopiedFile(filePath, status, status.getCopyFromURL());
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      context.getDeletedFiles().add(new SvnChangedFile(filePath, status));
    }
    else {
      String parentCopyFromURL = context.getParentCopyFromURL(filePath);
      if (parentCopyFromURL != null) {
        context.addCopiedFile(filePath, status, parentCopyFromURL);
      } else {
        processStatus(filePath, status, context);
      }
    }
  }

  // seems here we can only have a tree conflict; which can be marked on either path (?)
  // .. ok try to merge states
  private Change createMovedChange(final ContentRevision before, final ContentRevision after, final SVNStatus copiedStatus,
                                   final SVNStatus deletedStatus, @NotNull final SvnChangeProviderContext context) {
    return new ConflictedSvnChange(before, after, ConflictState.mergeState(getState(copiedStatus, context), getState(deletedStatus, context)),
                                  ((copiedStatus != null) && (copiedStatus.getTreeConflict() != null)) ? after.getFile() : before.getFile());
  }

  private Change createChange(final ContentRevision before, final ContentRevision after, final FileStatus fStatus,
                              final SVNStatus svnStatus, @NotNull final SvnChangeProviderContext context) {
    return new ConflictedSvnChange(before, after, fStatus, getState(svnStatus, context), after == null ? before.getFile() : after.getFile());
  }

  private LocallyDeletedChange createLocallyDeletedChange(@NotNull FilePath filePath, final SVNStatus status, @NotNull final SvnChangeProviderContext context) {
    return new SvnLocallyDeletedChange(filePath, getState(status, context));
  }

  // todo context method
  private static ConflictState getState(@Nullable final SVNStatus svnStatus, @NotNull final SvnChangeProviderContext context) {
    if (svnStatus == null) {
      return ConflictState.none;
    }

    final boolean treeConflict = svnStatus.getTreeConflict() != null;
    final boolean textConflict = SVNStatusType.STATUS_CONFLICTED == svnStatus.getContentsStatus();
    final boolean propertyConflict = SVNStatusType.STATUS_CONFLICTED == svnStatus.getPropertiesStatus();
    if (treeConflict) {
      context.reportTreeConflict(svnStatus);
    }

    return ConflictState.getInstance(treeConflict, textConflict, propertyConflict);
  }

  private void processStatus(final FilePath filePath, final SVNStatus status, @NotNull final SvnChangeProviderContext context) throws SVNException {
    final ChangelistBuilder builder = context.getBuilder();
    loadEntriesFile(filePath);
    if (status != null) {
      FileStatus fStatus = convertStatus(status, filePath.getIOFile());

      final SVNStatusType statusType = status.getContentsStatus();
      final SVNStatusType propStatus = status.getPropertiesStatus();
      if (statusType == SVNStatusType.STATUS_UNVERSIONED || statusType == SVNStatusType.UNKNOWN) {
        final VirtualFile file = filePath.getVirtualFile();
        if (file != null) {
          builder.processUnversionedFile(file);
        }
      }
      else if (statusType == SVNStatusType.STATUS_CONFLICTED ||
               statusType == SVNStatusType.STATUS_MODIFIED ||
               statusType == SVNStatusType.STATUS_REPLACED ||
               propStatus == SVNStatusType.STATUS_MODIFIED ||
               propStatus == SVNStatusType.STATUS_CONFLICTED) {
        builder.processChangeInList(createChange(SvnContentRevision.create(myVcs, filePath, status.getCommittedRevision()),
                                         CurrentContentRevision.create(filePath), fStatus, status, context), changeListNameFromStatus(status));
        checkSwitched(filePath, builder, status, fStatus);
      }
      else if (statusType == SVNStatusType.STATUS_ADDED) {
        builder.processChangeInList(createChange(null, CurrentContentRevision.create(filePath), fStatus, status, context), changeListNameFromStatus(status));
      }
      else if (statusType == SVNStatusType.STATUS_DELETED) {
        builder.processChangeInList(createChange(SvnContentRevision.create(myVcs, filePath, status.getCommittedRevision()), null, fStatus, status, context),
                                    changeListNameFromStatus(status));
      }
      else if (statusType == SVNStatusType.STATUS_MISSING) {
        builder.processLocallyDeletedFile(createLocallyDeletedChange(filePath, status, context));
      }
      else if (statusType == SVNStatusType.STATUS_IGNORED) {
        builder.processIgnoredFile(filePath.getVirtualFile());
      }
      else if (status.isCopied()) {
        //
      }
      else if ((fStatus == FileStatus.NOT_CHANGED || fStatus == FileStatus.SWITCHED) && statusType != SVNStatusType.STATUS_NONE) {
        VirtualFile file = filePath.getVirtualFile();
        if (file != null && FileDocumentManager.getInstance().isFileModifiedAndDocumentUnsaved(file)) {
          builder.processChangeInList(createChange(SvnContentRevision.create(myVcs, filePath, status.getCommittedRevision()),
                                           CurrentContentRevision.create(filePath), FileStatus.MODIFIED, status, context), changeListNameFromStatus(status));
        }
        checkSwitched(filePath, builder, status, fStatus);
      }
    }
  }

  private void checkSwitched(final FilePath filePath, final ChangelistBuilder builder, final SVNStatus status,
                             final FileStatus convertedStatus) {
    if (status.isSwitched() || (convertedStatus == FileStatus.SWITCHED)) {
      final VirtualFile virtualFile = filePath.getVirtualFile();
      if (virtualFile == null) return;
      final String switchUrl = status.getURL().toString();
      final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsRootFor(virtualFile);
      if (vcsRoot != null) {  // it will be null if we walked into an excluded directory
        String baseUrl = null;
        try {
          baseUrl = myBranchConfigurationManager.get(vcsRoot).getBaseName(switchUrl);
        }
        catch (VcsException e) {
          LOG.info(e);
        }
        builder.processSwitchedFile(virtualFile, baseUrl == null ? switchUrl : baseUrl, true);
      }
    }
  }

  private static FileStatus convertStatus(final SVNStatus status, final File file) throws SVNException {
    if (status == null) {
      return FileStatus.UNKNOWN;
    }
    if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) {
      return FileStatus.UNKNOWN;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MISSING) {
      return FileStatus.DELETED_FROM_FS;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL) {
      return SvnFileStatus.EXTERNAL;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
      return SvnFileStatus.OBSTRUCTED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED) {
      return FileStatus.IGNORED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
      return FileStatus.ADDED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      return FileStatus.DELETED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_REPLACED) {
      return SvnFileStatus.REPLACED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED ||
             status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
      if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED &&
        status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
        return FileStatus.MERGED_WITH_BOTH_CONFLICTS;
      } else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED) {
        return FileStatus.MERGED_WITH_CONFLICTS;
      }
      return FileStatus.MERGED_WITH_PROPERTY_CONFLICTS;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MODIFIED ||
             status.getPropertiesStatus() == SVNStatusType.STATUS_MODIFIED) {
      return FileStatus.MODIFIED;
    }
    else if (status.isSwitched()) {
      return FileStatus.SWITCHED;
    }
    else if (status.isCopied()) {
      return FileStatus.ADDED;
    }
    return FileStatus.NOT_CHANGED;
  }

  /**
   * Ensures that the contents of the 'entries' file is cached in the VFS, so that the VFS will send
   * correct events when the 'entries' file is changed externally (to be received by SvnEntriesFileListener)
   *
   * @param filePath the path of a changed file.
   */
  private static void loadEntriesFile(final FilePath filePath) {
    final FilePath parentPath = filePath.getParentPath();
    if (parentPath == null) {
      return;
    }
    File svnSubdirectory = new File(parentPath.getIOFile(), SvnUtil.SVN_ADMIN_DIR_NAME);
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    VirtualFile file = localFileSystem.refreshAndFindFileByIoFile(svnSubdirectory);
    if (file != null) {
      localFileSystem.refreshAndFindFileByIoFile(new File(svnSubdirectory, SvnUtil.ENTRIES_FILE_NAME));
    }
    if (filePath.isDirectory()) {
      svnSubdirectory = new File(filePath.getPath(), SvnUtil.SVN_ADMIN_DIR_NAME);
      file = localFileSystem.refreshAndFindFileByIoFile(svnSubdirectory);
      if (file != null) {
        localFileSystem.refreshAndFindFileByIoFile(new File(svnSubdirectory, SvnUtil.ENTRIES_FILE_NAME));
      }
    }
  }

  private static class SvnChangedFile {
    private final FilePath myFilePath;
    private final SVNStatus myStatus;
    private String myCopyFromURL;

    public SvnChangedFile(final FilePath filePath, final SVNStatus status) {
      myFilePath = filePath;
      myStatus = status;
    }

    public SvnChangedFile(final FilePath filePath, final SVNStatus status, final String copyFromURL) {
      myFilePath = filePath;
      myStatus = status;
      myCopyFromURL = copyFromURL;
    }

    public FilePath getFilePath() {
      return myFilePath;
    }

    public SVNStatus getStatus() {
      return myStatus;
    }

    public String getCopyFromURL() {
      if (myCopyFromURL == null) {
        return myStatus.getCopyFromURL();
      }
      return myCopyFromURL;
    }

    @Override
    public String toString() {
      return myFilePath.getPath();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final SvnChangedFile that = (SvnChangedFile)o;

      if (myFilePath != null ? !myFilePath.equals(that.myFilePath) : that.myFilePath != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myFilePath != null ? myFilePath.hashCode() : 0;
    }
  }

  private static class SvnChangeProviderContext {
    private final ChangelistBuilder myChangelistBuilder;
    private final SVNStatusClient myStatusClient;
    private List<SvnChangedFile> myCopiedFiles = null;
    private final List<SvnChangedFile> myDeletedFiles = new ArrayList<SvnChangedFile>();
    // for files moved in a subtree, which were the targets of merge (for instance). 
    private final Map<String, SVNStatus> myTreeConflicted;
    private Map<FilePath, String> myCopyFromURLs = null;

    private final ProgressIndicator myProgress;

    public SvnChangeProviderContext(SvnVcs vcs, final ChangelistBuilder changelistBuilder, final ProgressIndicator progress) {
      myStatusClient = vcs.createStatusClient();
      myChangelistBuilder = changelistBuilder;
      myProgress = progress;
      myTreeConflicted = new HashMap<String, SVNStatus>();
    }

    public ChangelistBuilder getBuilder() {
      return myChangelistBuilder;
    }

    public SVNStatusClient getClient() {
      return myStatusClient;
    }

    public void reportTreeConflict(final SVNStatus status) {
      myTreeConflicted.put(status.getFile().getAbsolutePath(), status);
    }

    @Nullable
    public SVNStatus getTreeConflictStatus(final File file) {
      return myTreeConflicted.get(file.getAbsolutePath());
    }

    @NotNull
    public List<SvnChangedFile> getCopiedFiles() {
      if (myCopiedFiles == null) {
        return Collections.emptyList();
      }
      return myCopiedFiles;
    }

    public List<SvnChangedFile> getDeletedFiles() {
      return myDeletedFiles;
    }

    public boolean isDeleted(final FilePath path) {
      for (SvnChangedFile deletedFile : myDeletedFiles) {
        if (Comparing.equal(path, deletedFile.getFilePath())) {
          return true;
        }
      }
      return false;
    }

    public boolean isCanceled() {
      return (myProgress != null) && myProgress.isCanceled();
    }

    /**
     * If the specified filepath or its parent was added with history, returns the URL of the copy source for this filepath.
     *
     * @param filePath the original filepath
     * @return the copy source url, or null if the file isn't a copy of anything
     */
    @Nullable
    public String getParentCopyFromURL(FilePath filePath) {
      if (myCopyFromURLs == null) {
        return null;
      }
      StringBuilder relPathBuilder = new StringBuilder();
      while(filePath != null) {
        String copyFromURL = myCopyFromURLs.get(filePath);
        if (copyFromURL != null) {
          return copyFromURL + relPathBuilder.toString();
        }
        relPathBuilder.insert(0, "/" + filePath.getName());
        filePath = filePath.getParentPath();
      }
      return null;
    }

    public void addCopiedFile(final FilePath filePath, final SVNStatus status, final String copyFromURL) {
      if (myCopiedFiles == null) {
        myCopiedFiles = new ArrayList<SvnChangedFile>();
      }
      myCopiedFiles.add(new SvnChangedFile(filePath, status, copyFromURL));
      final String url = status.getCopyFromURL();
      if (url != null) {
        addCopyFromURL(filePath, url);
      }
    }

    public void addCopyFromURL(final FilePath filePath, final String url) {
      if (myCopyFromURLs == null) {
        myCopyFromURLs = new HashMap<FilePath, String>();
      }
      myCopyFromURLs.put(filePath, url);
    }
  }
}
