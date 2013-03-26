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
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.dialogs.LockDialog;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class SvnUtil {
  @NonNls public static final String SVN_ADMIN_DIR_NAME = SVNFileUtil.getAdminDirectoryName();
  @NonNls public static final String ENTRIES_FILE_NAME = "entries";
  @NonNls public static final String WC_DB_FILE_NAME = "wc.db";
  @NonNls public static final String DIR_PROPS_FILE_NAME = "dir-props";
  @NonNls public static final String PATH_TO_LOCK_FILE = SVN_ADMIN_DIR_NAME + "/lock";
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnUtil");

  private SvnUtil() { }

  public static boolean isSvnVersioned(final Project project, File parent) {
    try {
      final SVNInfo info = SvnVcs.getInstance(project).createWCClient().doInfo(parent, SVNRevision.UNDEFINED);
      return info != null;
    }
    catch (SVNException e) {
      return false;
    }
  }

  public static Collection<VirtualFile> crawlWCRoots(final Project project, File path, SvnWCRootCrawler callback, ProgressIndicator progress) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile vf = lfs.findFileByIoFile(path);
    if (vf == null) {
      vf = lfs.refreshAndFindFileByIoFile(path);
    }
    if (vf == null) return Collections.emptyList();
    return crawlWCRoots(project, vf, callback, progress);
  }

  private static Collection<VirtualFile> crawlWCRoots(final Project project, VirtualFile vf, SvnWCRootCrawler callback, ProgressIndicator progress) {
    final Collection<VirtualFile> result = new HashSet<VirtualFile>();
    final boolean isDirectory = vf.isDirectory();
    VirtualFile parent = ! isDirectory || !vf.exists() ? vf.getParent() : vf;

    final File parentIo = new File(parent.getPath());
    if (isSvnVersioned(project, parentIo)) {
      checkCanceled(progress);
      File ioFile = new File(vf.getPath());
      callback.handleWorkingCopyRoot(ioFile, progress);
      checkCanceled(progress);
      result.add(parent);
    } else if (isDirectory) {
      checkCanceled(progress);
      final VirtualFile[] childrenVF = parent.getChildren();
      for (VirtualFile file : childrenVF) {
        checkCanceled(progress);
        if (file.isDirectory()) {
          result.addAll(crawlWCRoots(project, file, callback, progress));
        }
      }
    }
    return result;
  }

  private static void checkCanceled(final ProgressIndicator progress) {
    if (progress != null && progress.isCanceled()) {
      throw new ProcessCanceledException();
    }
  }

  @Nullable
  public static String getExactLocation(final SvnVcs vcs, File path) {
    try {
      SVNWCClient wcClient = vcs.createWCClient();
      SVNInfo info = wcClient.doInfo(path, SVNRevision.UNDEFINED);
      if (info != null && info.getURL() != null) {
        return info.getURL().toString();
      }
    }
    catch (SVNException ignored) { }
    return null;
  }

  public static Map<String, File> getLocationInfoForModule(final SvnVcs vcs, File path, ProgressIndicator progress) {
    final LocationsCrawler crawler = new LocationsCrawler(vcs);
    crawlWCRoots(vcs.getProject(), path, crawler, progress);
    return crawler.getLocationInfos();
  }

  public static void doLockFiles(Project project, final SvnVcs activeVcs, @NotNull final File[] ioFiles) throws VcsException {
    final String lockMessage;
    final boolean force;
    // TODO[yole]: check for shift pressed
    if (activeVcs.getCheckoutOptions().getValue()) {
      LockDialog dialog = new LockDialog(project, true, ioFiles.length > 1);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      lockMessage = dialog.getComment();
      force = dialog.isForce();
    }
    else {
      lockMessage = "";
      force = false;
    }

    final SVNException[] exception = new SVNException[1];
    final Collection<String> failedLocks = new ArrayList<String>();
    final int[] count = new int[]{ioFiles.length};
    final ISVNEventHandler eventHandler = new ISVNEventHandler() {
      public void handleEvent(SVNEvent event, double progress) {
        if (event.getAction() == SVNEventAction.LOCK_FAILED) {
          failedLocks.add(event.getErrorMessage() != null ?
                          event.getErrorMessage().getFullMessage() :
                          event.getFile().getAbsolutePath());
          count[0]--;
        }
      }

      public void checkCancelled() {
      }
    };

    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        SVNWCClient wcClient;

        try {
          wcClient = activeVcs.createWCClient();
          wcClient.setEventHandler(eventHandler);
          if (progress != null) {
            progress.setText(SvnBundle.message("progress.text.locking.files"));
          }
          for (File ioFile : ioFiles) {
            if (progress != null) {
              progress.checkCanceled();
            }
            if (progress != null) {
              progress.setText2(SvnBundle.message("progress.text2.processing.file", ioFile.getName()));
            }
            wcClient.doLock(new File[]{ioFile}, force, lockMessage);
          }
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };

    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.lock.files"), false, project);
    if (!failedLocks.isEmpty()) {
      String[] failedFiles = ArrayUtil.toStringArray(failedLocks);
      List<VcsException> exceptions = new ArrayList<VcsException>();
      for (String file : failedFiles) {
        exceptions.add(new VcsException(SvnBundle.message("exception.text.locking.file.failed", file)));
      }
      final StringBuilder sb = new StringBuilder(SvnBundle.message("message.text.files.lock.failed", failedFiles.length == 1 ? 0 : 1));
      for (VcsException vcsException : exceptions) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(vcsException.getMessage());
      }
      //AbstractVcsHelper.getInstance(project).showErrors(exceptions, SvnBundle.message("message.title.lock.failures"));
      throw new VcsException(sb.toString());
    }

    StatusBarUtil.setStatusBarInfo(project, SvnBundle.message("message.text.files.locked", count[0]));
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
  }

  public static void doUnlockFiles(Project project, final SvnVcs activeVcs, final File[] ioFiles) throws VcsException {
    final boolean force = true;
    final SVNException[] exception = new SVNException[1];
    final Collection<String> failedUnlocks = new ArrayList<String>();
    final int[] count = new int[]{ioFiles.length};
    final ISVNEventHandler eventHandler = new ISVNEventHandler() {
      public void handleEvent(SVNEvent event, double progress) {
        if (event.getAction() == SVNEventAction.UNLOCK_FAILED) {
          failedUnlocks.add(event.getErrorMessage() != null ?
                            event.getErrorMessage().getFullMessage() :
                            event.getFile().getAbsolutePath());
          count[0]--;
        }
      }

      public void checkCancelled() {
      }
    };

    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        SVNWCClient wcClient;

        try {
          wcClient = activeVcs.createWCClient();
          wcClient.setEventHandler(eventHandler);
          if (progress != null) {
            progress.setText(SvnBundle.message("progress.text.unlocking.files"));
          }
          for (File ioFile : ioFiles) {
            if (progress != null) {
              progress.checkCanceled();
            }
            if (progress != null) {
              progress.setText2(SvnBundle.message("progress.text2.processing.file", ioFile.getName()));
            }
            wcClient.doUnlock(new File[]{ioFile}, force);
          }
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };

    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.unlock.files"), false, project);
    if (!failedUnlocks.isEmpty()) {
      String[] failedFiles = ArrayUtil.toStringArray(failedUnlocks);
      List<VcsException> exceptions = new ArrayList<VcsException>();

      for (String file : failedFiles) {
        exceptions.add(new VcsException(SvnBundle.message("exception.text.failed.to.unlock.file", file)));
      }
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, SvnBundle.message("message.title.unlock.failures"));
    }

    StatusBarUtil.setStatusBarInfo(project, SvnBundle.message("message.text.files.unlocked", count[0]));
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
  }

  public static String formatRepresentation(final WorkingCopyFormat format) {
    if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(format)) {
      return SvnBundle.message("dialog.show.svn.map.table.version17.text");
    } else if (WorkingCopyFormat.ONE_DOT_SIX.equals(format)) {
      return SvnBundle.message("dialog.show.svn.map.table.version16.text");
    } else if (WorkingCopyFormat.ONE_DOT_FIVE.equals(format)) {
      return SvnBundle.message("dialog.show.svn.map.table.version15.text");
    } else if (WorkingCopyFormat.ONE_DOT_FOUR.equals(format)) {
      return SvnBundle.message("dialog.show.svn.map.table.version14.text");
    } else if (WorkingCopyFormat.ONE_DOT_THREE.equals(format)) {
      return SvnBundle.message("dialog.show.svn.map.table.version13.text");
    }
    return "";
  }

  public static Collection<List<Change>> splitChangesIntoWc(final SvnVcs vcs, final List<Change> changes) {
    return splitIntoRepositories(vcs, changes, new Convertor<Change, File>() {
      @Override
      public File convert(Change o) {
        return ChangesUtil.getFilePath(o).getIOFile();
      }
    });
  }

  public static Collection<List<File>> splitFilesIntoRepositories(final SvnVcs vcs, final List<File> committables) {
    return splitIntoRepositories(vcs, committables, Convertor.SELF);
  }

  public static <T> Collection<List<T>> splitIntoRepositories(final SvnVcs vcs, final List<T> committables,
                                                              Convertor<T, File> convertor) {
    if (committables.size() == 1) {
      return Collections.singletonList(committables);
    }

    final MultiMap<Pair<SVNURL, WorkingCopyFormat>, T> result = splitIntoRepositoriesMap(vcs, committables, convertor);

    if (result.size() == 1) {
      return Collections.singletonList(committables);
    }
    final Collection<List<T>> result2 = new ArrayList<List<T>>();
    for (Map.Entry<Pair<SVNURL, WorkingCopyFormat>, Collection<T>> entry : result.entrySet()) {
      result2.add((List<T>)entry.getValue());
    }
    return result2;
  }

  public static <T> MultiMap<Pair<SVNURL, WorkingCopyFormat>, T> splitIntoRepositoriesMap(SvnVcs vcs,
    List<T> committables, Convertor<T, File> convertor) {
    final MultiMap<Pair<SVNURL, WorkingCopyFormat>, T> result = new MultiMap<Pair<SVNURL, WorkingCopyFormat>, T>() {
      @Override
      protected Collection<T> createCollection() {
        return new ArrayList<T>();
      }
    };
    for (T committable : committables) {
      final RootUrlInfo path = vcs.getSvnFileUrlMapping().getWcRootForFilePath(convertor.convert(committable));
      if (path == null) {
        result.putValue(new Pair<SVNURL, WorkingCopyFormat>(null, null), committable);
      } else {
        result.putValue(new Pair<SVNURL, WorkingCopyFormat>(path.getRepositoryUrlUrl(), path.getFormat()), committable);
      }
    }
    return result;
  }

  private static class LocationsCrawler implements SvnWCRootCrawler {
    private final SvnVcs myVcs;
    private final Map<String, File> myLocations;

    public LocationsCrawler(SvnVcs vcs) {
      myVcs = vcs;
      myLocations = new HashMap<String, File>();
    }

    public Map<String, File> getLocationInfos() {
      return Collections.unmodifiableMap(myLocations);
    }

    public void handleWorkingCopyRoot(File root, ProgressIndicator progress) {
      String oldText = null;
      if (progress != null) {
        oldText = progress.getText();
        progress.setText(SvnBundle.message("progress.text.discovering.location", root.getAbsolutePath()));
      }
      try {
        SVNWCClient wcClient = myVcs.createWCClient();
        SVNInfo info = wcClient.doInfo(root, SVNRevision.UNDEFINED);
        if (info != null && info.getURL() != null) {
          myLocations.put(info.getURL().toString(), info.getFile());
        }
      }
      catch (SVNException e) {
        //
      }
      if (progress != null) {
        progress.setText(oldText);
      }
    }
  }

  @Nullable
  public static String getRepositoryUUID(final SvnVcs vcs, final File file) {
    final SVNWCClient client = vcs.createWCClient();
    try {
      final SVNInfo info = client.doInfo(file, SVNRevision.UNDEFINED);
      return (info == null) ? null : info.getRepositoryUUID();
    } catch (SVNException e) {
      return null;
    }
  }

  @Nullable
  public static String getRepositoryUUID(final SvnVcs vcs, final SVNURL url) {
    final SVNWCClient client = vcs.createWCClient();
    try {
      final SVNInfo info = client.doInfo(url, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED);
      return (info == null) ? null : info.getRepositoryUUID();
    } catch (SVNException e) {
      return null;
    }
  }

  @Nullable
  public static SVNURL getRepositoryRoot(final SvnVcs vcs, final File file) {
    final SVNWCClient client = vcs.createWCClient();
    try {
      final SVNInfo info = client.doInfo(file, SVNRevision.UNDEFINED);
      return (info == null) ? null : info.getRepositoryRootURL();
    } catch (SVNException e) {
      return null;
    }
  }

  @Nullable
  public static SVNURL getRepositoryRoot(final SvnVcs vcs, final String url) {
    try {
      return getRepositoryRoot(vcs, SVNURL.parseURIEncoded(url), true);
    }
    catch (SVNException e) {
      return null;
    }
  }

  @Nullable
  public static SVNURL getRepositoryRoot(final SvnVcs vcs, final SVNURL url, boolean allowRemote) throws SVNException {
    final SVNWCClient client = vcs.createWCClient();
    SVNInfo info = client.doInfo(url, SVNRevision.UNDEFINED, SVNRevision.HEAD);
    return (info == null) ? null : info.getRepositoryRootURL();
  }

  public static boolean isWorkingCopyRoot(final File file) {
    try {
      return SVNWCUtil.isWorkingCopyRoot(file);
    } catch (SVNException e) {
      return false;
    }
  }

  @Nullable
  public static File getWorkingCopyRoot(final File inFile) {
    File file = inFile;
    while ((file != null) && (file.isFile() || (! file.exists()))) {
      file = file.getParentFile();
    }

    if (file == null) {
      return null;
    }

    File workingCopyRoot = null;
    try {
      workingCopyRoot = SVNWCUtil.getWorkingCopyRoot(file, true);
    } catch (SVNException e) {
      //
    }
    if (workingCopyRoot == null) {
     workingCopyRoot = getWcCopyRootIf17(file, null);
    }
    return workingCopyRoot;
  }

  public static File fileFromUrl(final File baseDir, final String baseUrl, final String fullUrl) throws SVNException {
    assert fullUrl.startsWith(baseUrl);

    final String part = fullUrl.substring(baseUrl.length()).replace('/', File.separatorChar).replace('\\', File.separatorChar);
    return new File(baseDir, part);
  }

  public static VirtualFile getVirtualFile(final String filePath) {
    @NonNls final String path = VfsUtilCore.pathToUrl(filePath.replace(File.separatorChar, '/'));
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Nullable
      public VirtualFile compute() {
        return VirtualFileManager.getInstance().findFileByUrl(path);
      }
    });
  }

  @Nullable
  public static SVNURL getBranchForUrl(final SvnVcs vcs, final VirtualFile vcsRoot, final String urlPath) {
    final SvnBranchConfigurationNew configuration;
    try {
      final SVNURL url = SVNURL.parseURIEncoded(urlPath);
      configuration = SvnBranchConfigurationManager.getInstance(vcs.getProject()).get(vcsRoot);
      return (configuration == null) ? null : configuration.getWorkingBranch(url);
    }
    catch (SVNException e) {
      return null;
    } catch (VcsException e1) {
      return null;
    }
  }

  @Nullable
  public static String getPathForProgress(final SVNEvent event) {
    if (event.getFile() != null) {
      return event.getFile().getName();
    }
    if (event.getURL() != null) {
      return event.getURL().toString();
    }
    return null;
  }

  @Nullable
  public static VirtualFile correctRoot(final Project project, final VirtualFile file) {
    if (file.getPath().length() == 0) {
      // project root
      return project.getBaseDir();
    }
    return file;
  }

  public static boolean checkRepositoryVersion15(final SvnVcs vcs, final String url) {
    SVNRepository repository = null;
    try {
      repository = vcs.createRepository(url);
      return repository.hasCapability(SVNCapability.MERGE_INFO);
    }
    catch (SVNException e) {
      return false;
    }
    finally {
      if (repository != null) {
        repository.closeSession();
      }
    }
  }

  @Nullable
  public static SVNStatus getStatus(final SvnVcs vcs, final File file) {
    final SVNStatusClient statusClient = vcs.createStatusClient();
    try {
      return statusClient.doStatus(file, false);
    }
    catch (SVNException e) {
      return null;
    }
  }

  public static SVNDepth getDepth(final SvnVcs vcs, final File file) {
    final SVNWCClient client = vcs.createWCClient();
    try {
      final SVNInfo svnInfo = client.doInfo(file, SVNRevision.UNDEFINED);
      if (svnInfo != null) {
        return svnInfo.getDepth();
      }
    }
    catch (SVNException e) {
      //
    }
    return SVNDepth.UNKNOWN;
  }

  public static boolean seemsLikeVersionedDir(final VirtualFile file) {
    final String adminName = SVNFileUtil.getAdminDirectoryName();
    final VirtualFile child = file.findChild(adminName);
    return child != null && child.isDirectory();
  }

  public static boolean isAdminDirectory(final VirtualFile file) {
    return isAdminDirectory(file.getParent(), file.getName());
  }

  public static boolean isAdminDirectory(VirtualFile parent, String name) {
    // never allow to delete admin directories by themselves (this can happen during VCS undo,
    // which deletes created directories from bottom to top)
    if (name.equals(SVN_ADMIN_DIR_NAME)) {
      return true;
    }
    if (parent != null) {
      if (parent.getName().equals(SVN_ADMIN_DIR_NAME)) {
        return true;
      }
      parent = parent.getParent();
      if (parent != null && parent.getName().equals(SVN_ADMIN_DIR_NAME)) {
        return true;
      }
    }
    return false;
  }

  public static SVNURL getCommittedURL(final SvnVcs vcs, final File file) {
    final File root = getWorkingCopyRoot(file);
    if (root == null) return null;
    return getUrl(vcs, root);
  }

  @Nullable
  public static SVNURL getUrl(final SvnVcs vcs, final File file) {
    try {
      final SVNInfo info = vcs.createWCClient().doInfo(file, SVNRevision.UNDEFINED);
      return info == null ? null : info.getURL(); // todo for moved items?
    }
    catch (SVNException e) {
      LOG.debug(e);
      return null;
    }
  }

  public static boolean doesRepositorySupportMergeInfo(final SvnVcs vcs, final SVNURL url) {
    SVNRepository repository = null;
    try {
      repository = vcs.createRepository(url);
      return repository.hasCapability(SVNCapability.MERGE_INFO);
    }
    catch (SVNException e) {
      return false;
    } finally {
      if (repository != null) {
        repository.closeSession();
      }
    }
  }

  public static boolean remoteFolderIsEmpty(final SvnVcs vcs, final String url) throws SVNException {
    SVNRepository repository = null;
    try {
      repository = vcs.createRepository(url);
      final Ref<Boolean> result = new Ref<Boolean>(true);
      repository.getDir("", -1, null, new ISVNDirEntryHandler() {
        public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
          if (dirEntry != null) {
            result.set(false);
          }
        }
      });
      return result.get();
    } finally {
      if (repository != null) {
        repository.closeSession();
      }
    }
  }

  public static File getWcDb(final File file) {
    return new File(file, SVN_ADMIN_DIR_NAME + "/wc.db");
  }

  @Nullable
  public static File getWcCopyRootIf17(final File file, @Nullable final File upperBound) {
    File current = file;
    boolean wcDbFound = false;
    while (current != null) {
      File wcDb;
      if ((wcDb = getWcDb(current)).exists() && ! wcDb.isDirectory()) {
        wcDbFound = true;
        break;
      }
      current = current.getParentFile();
    }
    if (! wcDbFound) return null;
    while (current != null) {
      try {
        final SvnWcGeneration svnWcGeneration = SvnOperationFactory.detectWcGeneration(current, false);
        if (SvnWcGeneration.V17.equals(svnWcGeneration)) return current;
        if (SvnWcGeneration.V16.equals(svnWcGeneration)) return null;
        if (upperBound != null && FileUtil.filesEqual(upperBound, current)) return null;
        current = current.getParentFile();
      }
      catch (SVNException e) {
        return null;
      }
    }
    return null;
  }

  public static boolean is17CopyPart(final File file) {
    try {
      return SvnWcGeneration.V17.equals(SvnOperationFactory.detectWcGeneration(file, true));
    }
    catch (SVNException e) {
      return false;
    }
  }

  public static String appendMultiParts(@NotNull final String base, @NotNull final String subPath) throws SVNException {
    if (StringUtil.isEmpty(subPath)) return base;
    final List<String> parts = StringUtil.split(subPath.replace('\\', '/'), "/", true);
    String result = base;
    for (String part : parts) {
      result = SVNPathUtil.append(result, part);
    }
    return result;
  }

  public static SVNURL appendMultiParts(@NotNull final SVNURL base, @NotNull final String subPath) throws SVNException {
    if (StringUtil.isEmpty(subPath)) return base;
    final List<String> parts = StringUtil.split(subPath.replace('\\', '/'), "/", true);
    SVNURL result = base;
    for (String part : parts) {
      result = result.appendPath(part, false);
    }
    return result;
  }

  public static byte[] getFileContents(final SvnVcs vcs, final String path, final boolean isUrl, final SVNRevision revision,
                                       final SVNRevision pegRevision)
    throws VcsException {
    final int maxSize = VcsUtil.getMaxVcsLoadedFileSize();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream() {
      @Override
      public synchronized void write(int b) {
        if (size() > maxSize) throw new FileTooBigRuntimeException();
        super.write(b);
      }

      @Override
      public synchronized void write(byte[] b, int off, int len) {
        if (size() > maxSize) throw new FileTooBigRuntimeException();
        super.write(b, off, len);
      }

      @Override
      public synchronized void writeTo(OutputStream out) throws IOException {
        if (size() > maxSize) throw new FileTooBigRuntimeException();
        super.writeTo(out);
      }
    };
    SVNWCClient wcClient = vcs.createWCClient();
    try {
      if (isUrl) {
        wcClient.doGetFileContents(SVNURL.parseURIEncoded(path), pegRevision, revision, true, buffer);
      } else {
        wcClient.doGetFileContents(new File(path), pegRevision, revision, true, buffer);
      }
      ContentRevisionCache.checkContentsSize(path, buffer.size());
    } catch (FileTooBigRuntimeException e) {
      ContentRevisionCache.checkContentsSize(path, buffer.size());
    } catch (SVNException e) {
      throw new VcsException(e);
    }
    return buffer.toByteArray();
  }

  private static class FileTooBigRuntimeException extends RuntimeException {}
}
