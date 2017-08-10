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
package org.jetbrains.idea.svn;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.LockDialog;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.Status;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.schema.SqlJetSchema;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeSchemaTable;
import org.tmatesoft.sqljet.core.internal.table.SqlJetBtreeSchemaTable;
import org.tmatesoft.sqljet.core.table.ISqlJetOptions;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.sqljet.core.table.engine.SqlJetEngine;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.net.URI;
import java.nio.channels.NonWritableChannelException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.map2Array;
import static com.intellij.util.containers.ContainerUtil.newHashSet;
import static java.util.Collections.emptyList;

public class SvnUtil {
  // TODO: ASP.NET hack behavior should be supported - http://svn.apache.org/repos/asf/subversion/trunk/notes/asp-dot-net-hack.txt
  // TODO: Remember this when moving out SVNKit classes.
  @NonNls public static final String SVN_ADMIN_DIR_NAME = SVNFileUtil.getAdminDirectoryName();
  @NonNls public static final String ENTRIES_FILE_NAME = "entries";
  @NonNls public static final String WC_DB_FILE_NAME = "wc.db";
  @NonNls public static final String PATH_TO_LOCK_FILE = SVN_ADMIN_DIR_NAME + "/lock";
  public static final int DEFAULT_PORT_INDICATOR = -1;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnUtil");

  public static final Pattern ERROR_PATTERN = Pattern.compile("^svn: (E(\\d+)): (.*)$", Pattern.MULTILINE);
  public static final Pattern WARNING_PATTERN = Pattern.compile("^svn: warning: (W(\\d+)): (.*)$", Pattern.MULTILINE);

  private static final Pair<SVNURL, WorkingCopyFormat> UNKNOWN_REPOSITORY_AND_FORMAT = Pair.create(null, WorkingCopyFormat.UNKNOWN);

  private SvnUtil() { }

  @Nullable
  public static SVNErrorMessage parseWarning(@NotNull String text) {
    Matcher matcher = WARNING_PATTERN.matcher(text);
    SVNErrorMessage error = null;

    // currently treating only first warning
    if (matcher.find()) {
      error = SVNErrorMessage
        .create(SVNErrorCode.getErrorCode(Integer.parseInt(matcher.group(2))), matcher.group(3), SVNErrorMessage.TYPE_WARNING);
    }

    return error;
  }

  public static boolean isSvnVersioned(@NotNull SvnVcs vcs, @NotNull File file) {
    return vcs.getInfo(file) != null;
  }

  @NotNull
  public static Collection<VirtualFile> crawlWCRoots(@NotNull SvnVcs vcs,
                                                     @NotNull File path,
                                                     @NotNull SvnWCRootCrawler callback,
                                                     @Nullable ProgressIndicator progress) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path);

    return file != null ? crawlWCRoots(vcs, file, callback, progress) : emptyList();
  }

  @NotNull
  private static Collection<VirtualFile> crawlWCRoots(@NotNull SvnVcs vcs,
                                                      @NotNull VirtualFile file,
                                                      @NotNull SvnWCRootCrawler callback,
                                                      @Nullable ProgressIndicator progress) {
    Set<VirtualFile> result = newHashSet();
    // TODO: Actually it is not OK to call getParent() if file is invalid.
    VirtualFile parent = !file.isDirectory() || !file.isValid() ? file.getParent() : file;

    if (isSvnVersioned(vcs, virtualToIoFile(parent))) {
      checkCanceled(progress);
      callback.handleWorkingCopyRoot(virtualToIoFile(file), progress);
      checkCanceled(progress);
      result.add(parent);
    }
    else if (file.isDirectory()) {
      checkCanceled(progress);
      for (VirtualFile child : parent.getChildren()) {
        checkCanceled(progress);
        if (child.isDirectory()) {
          result.addAll(crawlWCRoots(vcs, child, callback, progress));
        }
      }
    }

    return result;
  }

  private static void checkCanceled(@Nullable ProgressIndicator progress) {
    if (progress != null && progress.isCanceled()) {
      throw new ProcessCanceledException();
    }
  }

  @Nullable
  public static String getExactLocation(final SvnVcs vcs, File path) {
    Info info = vcs.getInfo(path);
    return info != null && info.getURL() != null ? info.getURL().toString() : null;
  }

  @NotNull
  public static File[] toIoFiles(@NotNull VirtualFile[] files) {
    return map2Array(files, File.class, VfsUtilCore::virtualToIoFile);
  }

  public static void doLockFiles(Project project, final SvnVcs activeVcs, @NotNull final File[] ioFiles) throws VcsException {
    final String lockMessage;
    final boolean force;
    // TODO[yole]: check for shift pressed
    if (activeVcs.getCheckoutOptions().getValue()) {
      LockDialog dialog = new LockDialog(project, true, ioFiles.length > 1);
      if (!dialog.showAndGet()) {
        return;
      }
      lockMessage = dialog.getComment();
      force = dialog.isForce();
    }
    else {
      lockMessage = "";
      force = false;
    }

    final VcsException[] exception = new VcsException[1];
    final Collection<String> failedLocks = new ArrayList<>();
    final int[] count = new int[]{ioFiles.length};
    final ProgressTracker eventHandler = new ProgressTracker() {
      public void consume(ProgressEvent event) {
        if (event.getAction() == EventAction.LOCK_FAILED) {
          failedLocks.add(event.getErrorMessage() != null ?
                          event.getErrorMessage().getFullMessage() :
                          event.getFile().getAbsolutePath());
          count[0]--;
        }
      }

      public void checkCancelled() {
      }
    };

    Runnable command = () -> {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

      try {
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
          activeVcs.getFactory(ioFile).createLockClient().lock(ioFile, force, lockMessage, eventHandler);
        }
      }
      catch (VcsException e) {
        exception[0] = e;
      }
    };

    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.lock.files"), false, project);
    if (!failedLocks.isEmpty()) {
      String[] failedFiles = ArrayUtil.toStringArray(failedLocks);
      List<VcsException> exceptions = new ArrayList<>();
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
      throw exception[0];
    }
  }

  public static void doUnlockFiles(Project project, final SvnVcs activeVcs, final File[] ioFiles) throws VcsException {
    final boolean force = true;
    final VcsException[] exception = new VcsException[1];
    final Collection<String> failedUnlocks = new ArrayList<>();
    final int[] count = new int[]{ioFiles.length};
    final ProgressTracker eventHandler = new ProgressTracker() {
      public void consume(ProgressEvent event) {
        if (event.getAction() == EventAction.UNLOCK_FAILED) {
          failedUnlocks.add(event.getErrorMessage() != null ?
                            event.getErrorMessage().getFullMessage() :
                            event.getFile().getAbsolutePath());
          count[0]--;
        }
      }

      public void checkCancelled() {
      }
    };

    Runnable command = () -> {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

      try {
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
          activeVcs.getFactory(ioFile).createLockClient().unlock(ioFile, force, eventHandler);
        }
      }
      catch (VcsException e) {
        exception[0] = e;
      }
    };

    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.unlock.files"), false, project);
    if (!failedUnlocks.isEmpty()) {
      String[] failedFiles = ArrayUtil.toStringArray(failedUnlocks);
      List<VcsException> exceptions = new ArrayList<>();

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

  @NotNull
  public static MultiMap<Pair<SVNURL, WorkingCopyFormat>, Change> splitChangesIntoWc(@NotNull SvnVcs vcs, @NotNull List<Change> changes) {
    return splitIntoRepositoriesMap(vcs, changes, change -> ChangesUtil.getFilePath(change));
  }

  @NotNull
  public static <T> MultiMap<Pair<SVNURL, WorkingCopyFormat>, T> splitIntoRepositoriesMap(@NotNull final SvnVcs vcs,
                                                                                          @NotNull Collection<T> items,
                                                                                          @NotNull final Convertor<T, FilePath> converter) {
    return ContainerUtil.groupBy(items, item -> {
      RootUrlInfo path = vcs.getSvnFileUrlMapping().getWcRootForFilePath(converter.convert(item).getIOFile());

      return path == null ? UNKNOWN_REPOSITORY_AND_FORMAT : Pair.create(path.getRepositoryUrlUrl(), path.getFormat());
    });
  }

  /**
   * Gets working copy internal format. Works for 1.7 and 1.8.
   *
   * @param path
   * @return
   */
  @NotNull
  public static WorkingCopyFormat getFormat(final File path) {
    WorkingCopyFormat result = null;
    File dbFile = resolveDatabase(path);

    if (dbFile != null) {
      result = FileUtilRt.doIOOperation(
        Registry.is("svn.use.sqlite.jdbc") ? new SqLiteJdbcWorkingCopyFormatOperation(dbFile) : new WorkingCopyFormatOperation(dbFile));

      if (result == null) {
        notifyDatabaseError();
      }
    }

    return result != null ? result : WorkingCopyFormat.UNKNOWN;
  }

  private static void close(@Nullable SqlJetDb db) {
    if (db != null) {
      try {
        db.close();
      }
      catch (SqlJetException e) {
        notifyDatabaseError();
      }
    }
  }

  private static void notifyDatabaseError() {
    VcsBalloonProblemNotifier.NOTIFICATION_GROUP
      .createNotification("Some errors occurred while accessing svn working copy database.", NotificationType.ERROR).notify(null);
  }

  private static File resolveDatabase(final File path) {
    File dbFile = getWcDb(path);
    File result = null;

    try {
      if (dbFile.exists() && dbFile.isFile()) {
        result = dbFile;
      }
    } catch (SecurityException e) {
      LOG.error("Failed to access working copy database", e);
    }

    return result;
  }

  @Nullable
  public static String getRepositoryUUID(final SvnVcs vcs, final File file) {
    final Info info = vcs.getInfo(file);
    return info != null ? info.getRepositoryUUID() : null;
  }

  @Nullable
  public static String getRepositoryUUID(final SvnVcs vcs, final SVNURL url) {
    try {
      final Info info = vcs.getInfo(url, SVNRevision.UNDEFINED);

      return (info == null) ? null : info.getRepositoryUUID();
    }
    catch (SvnBindException e) {
      return null;
    }
  }

  @Nullable
  public static SVNURL getRepositoryRoot(final SvnVcs vcs, final File file) {
    final Info info = vcs.getInfo(file);
    return info != null ? info.getRepositoryRootURL() : null;
  }

  @Nullable
  public static SVNURL getRepositoryRoot(final SvnVcs vcs, final String url) {
    try {
      return getRepositoryRoot(vcs, createUrl(url));
    }
    catch (SvnBindException e) {
      return null;
    }
  }

  @Nullable
  public static SVNURL getRepositoryRoot(final SvnVcs vcs, final SVNURL url) throws SvnBindException {
    Info info = vcs.getInfo(url, SVNRevision.HEAD);

    return (info == null) ? null : info.getRepositoryRootURL();
  }

  public static boolean isWorkingCopyRoot(final File file) {
    return FileUtil.filesEqual(file, getWorkingCopyRootNew(file));
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

  @NotNull
  public static File fileFromUrl(final File baseDir, final String baseUrl, final String fullUrl) {
    assert fullUrl.startsWith(baseUrl);

    final String part = fullUrl.substring(baseUrl.length()).replace('/', File.separatorChar).replace('\\', File.separatorChar);
    return new File(baseDir, part);
  }

  public static VirtualFile getVirtualFile(final String filePath) {
    @NonNls final String path = VfsUtilCore.pathToUrl(filePath.replace(File.separatorChar, '/'));
    return ReadAction.compute(() -> VirtualFileManager.getInstance().findFileByUrl(path));
  }

  @Nullable
  public static SVNURL getBranchForUrl(@NotNull SvnVcs vcs, @NotNull VirtualFile vcsRoot, @NotNull String urlValue) {
    SVNURL url = null;

    try {
      url = createUrl(urlValue);
    }
    catch (SvnBindException e) {
      LOG.debug(e);
    }

    return url != null ? getBranchForUrl(vcs, vcsRoot, url) : null;
  }

  @Nullable
  public static SVNURL getBranchForUrl(@NotNull SvnVcs vcs, @NotNull VirtualFile vcsRoot, @NotNull SVNURL url) {
    SVNURL result = null;
    SvnBranchConfigurationNew configuration = SvnBranchConfigurationManager.getInstance(vcs.getProject()).get(vcsRoot);

    try {
      result = configuration.getWorkingBranch(url);
    }
    catch (SvnBindException e) {
      LOG.debug(e);
    }

    return result;
  }

  public static boolean checkRepositoryVersion15(@NotNull SvnVcs vcs, @NotNull String url) {
    // Merge info tracking is supported in repositories since svn 1.5 (June 2008) - see http://subversion.apache.org/docs/release-notes/.
    // But still some users use 1.4 repositories and currently we need to know if repository supports merge info for some code flows.

    boolean result = false;

    try {
      result = vcs.getFactory().createRepositoryFeaturesClient().supportsMergeTracking(createUrl(url));
    }
    catch (VcsException e) {
      LOG.info(e);
      // TODO: Exception is thrown when url just not exist (was deleted, for instance) => and false is returned which seems not to be correct.
    }

    return result;
  }

  @Nullable
  public static Status getStatus(@NotNull final SvnVcs vcs, @NotNull final File file) {
    try {
      return vcs.getFactory(file).createStatusClient().doStatus(file, false);
    }
    catch (SvnBindException e) {
      return null;
    }
  }

  @NotNull
  public static Depth getDepth(final SvnVcs vcs, final File file) {
    Info info = vcs.getInfo(file);

    return info != null && info.getDepth() != null ? info.getDepth() : Depth.UNKNOWN;
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

  @Nullable
  public static SVNURL getUrl(final SvnVcs vcs, final File file) {
    // todo for moved items?
    final Info info = vcs.getInfo(file);

    return info == null ? null : info.getURL();
  }

  public static boolean remoteFolderIsEmpty(@NotNull SvnVcs vcs, @NotNull String url) throws VcsException {
    SvnTarget target = SvnTarget.fromURL(createUrl(url));
    Ref<Boolean> result = new Ref<>(true);
    DirectoryEntryConsumer handler = entry -> {
      if (entry != null) {
        result.set(false);
      }
    };

    vcs.getFactory(target).createBrowseClient().list(target, null, Depth.IMMEDIATES, handler);
    return result.get();
  }

  public static File getWcDb(final File file) {
    return new File(file, SVN_ADMIN_DIR_NAME + "/wc.db");
  }

  @Nullable
  public static File getWcCopyRootIf17(final File file, @Nullable final File upperBound) {
    File current = getParentWithDb(file);
    if (current == null) return null;

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

  /**
   * Utility method that deals also with 1.8 working copies.
   * TODO: Should be renamed when all parts updated for 1.8.
   *
   * @param file
   * @return
   */
  @Nullable
  public static File getWorkingCopyRootNew(final File file) {
    File current = getParentWithDb(file);
    if (current == null) return getWorkingCopyRoot(file);

    WorkingCopyFormat format = getFormat(current);

    return format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN) ? current : getWorkingCopyRoot(file);
  }

  private static File getParentWithDb(File file) {
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
    return current;
  }

  public static String getRelativeUrl(@NotNull String parentUrl, @NotNull String childUrl) {
    return FileUtilRt.getRelativePath(parentUrl, childUrl, '/', true);
  }

  public static String getRelativePath(@NotNull String parentPath, @NotNull String childPath) {
    return  FileUtilRt.getRelativePath(FileUtil.toSystemIndependentName(parentPath), FileUtil.toSystemIndependentName(childPath), '/');
  }

  @NotNull
  @Contract(pure = true)
  public static String ensureStartSlash(@NotNull String path) {
    return StringUtil.startsWithChar(path, '/') ? path : '/' + path;
  }

  @NotNull
  public static String join(@NotNull final String... parts) {
    return StringUtil.join(parts, "/");
  }

  public static String appendMultiParts(@NotNull final String base, @NotNull final String subPath) {
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

  @NotNull
  public static SVNURL removePathTail(@NotNull SVNURL url) throws SvnBindException {
    return createUrl(SVNPathUtil.removeTail(url.toDecodedString()));
  }

  @NotNull
  public static SVNRevision getHeadRevision(@NotNull SvnVcs vcs, @NotNull SVNURL url) throws SvnBindException {
    Info info = vcs.getInfo(url, SVNRevision.HEAD);

    if (info == null) {
      throw new SvnBindException("Could not get info for " + url);
    }
    if (info.getRevision() == null) {
      throw new SvnBindException("Could not get revision for " + url);
    }

    return info.getRevision();
  }

  public static byte[] getFileContents(@NotNull final SvnVcs vcs,
                                       @NotNull final SvnTarget target,
                                       @Nullable final SVNRevision revision,
                                       @Nullable final SVNRevision pegRevision)
    throws VcsException {
    return vcs.getFactory(target).createContentClient().getContent(target, revision, pegRevision);
  }

  public static boolean hasDefaultPort(@NotNull SVNURL result) {
    return !result.hasPort() || SVNURL.getDefaultPortNumber(result.getProtocol()) == result.getPort();
  }

  /**
   * When creating SVNURL with default port, some negative value should be specified as port number, otherwise specified port value (even
   * if equals to default) will occur in toString() result.
   */
  public static int resolvePort(@NotNull SVNURL url) {
    return !hasDefaultPort(url) ? url.getPort() : DEFAULT_PORT_INDICATOR;
  }

  @NotNull
  public static SVNURL createUrl(@NotNull String url) throws SvnBindException {
    return createUrl(url, true);
  }

  @NotNull
  public static SVNURL createUrl(@NotNull String url, boolean encoded) throws SvnBindException {
    try {
      SVNURL result = encoded ? SVNURL.parseURIEncoded(url) : SVNURL.parseURIDecoded(url);

      // explicitly check if port corresponds to default port and recreate url specifying default port indicator
      if (result.hasPort() && hasDefaultPort(result)) {
        result = SVNURL
          .create(result.getProtocol(), result.getUserInfo(), result.getHost(), DEFAULT_PORT_INDICATOR, result.getURIEncodedPath(), true);
      }

      return result;
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @NotNull
  public static SVNURL parseUrl(@NotNull String url) {
    try {
      return SVNURL.parseURIEncoded(url);
    }
    catch (SVNException e) {
      throw createIllegalArgument(e);
    }
  }

  @NotNull
  public static SVNURL append(@NotNull SVNURL parent, @NotNull String child) throws SvnBindException {
    try {
      return parent.appendPath(child, false);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  public static IllegalArgumentException createIllegalArgument(SVNException e) {
    IllegalArgumentException runtimeException = new IllegalArgumentException();
    runtimeException.initCause(e);
    return runtimeException;
  }

  @Nullable
  public static String getChangelistName(@NotNull final Status status) {
    // no explicit check on working copy format supports change lists as they are supported from svn 1.5
    // and anyway status.getChangelistName() should just return null if change lists are not supported.
    return status.getKind().isFile() ? status.getChangelistName() : null;
  }

  public static boolean isUnversionedOrNotFound(@NotNull SvnBindException e) {
    return e.contains(SVNErrorCode.WC_PATH_NOT_FOUND) ||
           e.contains(SVNErrorCode.UNVERSIONED_RESOURCE) ||
           e.contains(SVNErrorCode.WC_NOT_WORKING_COPY) ||
           // thrown when getting info from repository for non-existent item - like HEAD revision for deleted file
           e.contains(SVNErrorCode.ILLEGAL_TARGET) ||
           // for svn 1.6
           StringUtil.containsIgnoreCase(e.getMessage(), "(not a versioned resource)");
  }

  // TODO: Create custom Target class and implement append there
  @NotNull
  public static SvnTarget append(@NotNull SvnTarget target, @NotNull String path) throws SvnBindException {
    return append(target, path, false);
  }

  @NotNull
  public static SvnTarget append(@NotNull SvnTarget target, @NotNull String path, boolean checkAbsolute) throws SvnBindException {
    SvnTarget result;

    if (target.isFile()) {
      result = SvnTarget.fromFile(resolvePath(target.getFile(), path));
    }
    else {
      try {
        result = SvnTarget
          .fromURL(checkAbsolute && URI.create(path).isAbsolute() ? SVNURL.parseURIEncoded(path) : target.getURL().appendPath(path, false));
      }
      catch (SVNException e) {
        throw new SvnBindException(e);
      }
    }

    return result;
  }

  @NotNull
  public static File resolvePath(@NotNull File base, @NotNull String path) {
    File result = new File(path);

    if (!result.isAbsolute()) {
      result = ".".equals(path) ? base : new File(base, path);
    }

    return result;
  }

  /**
   * {@code SvnTarget.getPathOrUrlDecodedString} does not correctly work for URL targets - {@code SVNURL.toString} instead of
   * {@code SVNURL.toDecodedString} is used.
   * <p/>
   * Current utility method fixes this case.
   */
  @NotNull
  public static String toDecodedString(@NotNull SvnTarget target) {
    return target.isFile() ? target.getFile().getPath() : target.getURL().toDecodedString();
  }

  private static class WorkingCopyFormatOperation implements FileUtilRt.RepeatableIOOperation<WorkingCopyFormat, RuntimeException> {
    @NotNull private final File myDbFile;

    public WorkingCopyFormatOperation(@NotNull File dbFile) {
      myDbFile = dbFile;
    }

    @Nullable
    @Override
    public WorkingCopyFormat execute(boolean lastAttempt) {
      SqLiteDb db = null;
      int userVersion = 0;

      try {
        // "write" access is requested here for now as workaround - see some details
        // in https://code.google.com/p/sqljet/issues/detail?id=25 and http://issues.tmatesoft.com/issue/SVNKIT-418.
        // BUSY error is currently handled same way as others.
        db = SqLiteDb.open(myDbFile, true);
        userVersion = db.getOptions().getUserVersion();
      }
      catch (NonWritableChannelException e) {
        // Such exceptions could be thrown when db is opened in "read" mode, but the db file is readonly (for instance, locked
        // by other process). See links above for some details.
        // handle this exception type separately - not to break execution flow
        LOG.info(e);
      }
      catch (SqlJetException e) {
        LOG.info(e);
        if (db != null) {
          // Even if there is an error in db schema, db options could already be read successfully - so we just use them
          userVersion = db.getUserVersion();
          LOG.debug("Working copy database schema: " + db.getDbSchema());
        }
      }
      finally {
        close(db);
      }

      WorkingCopyFormat format = WorkingCopyFormat.getInstance(userVersion);

      return !WorkingCopyFormat.UNKNOWN.equals(format) ? format : null;
    }
  }

  private static class SqLiteJdbcWorkingCopyFormatOperation
    implements FileUtilRt.RepeatableIOOperation<WorkingCopyFormat, RuntimeException> {
    private static final String SQLITE_JDBC_TEMP_DIR_PROPERTY = "org.sqlite.tmpdir";

    @NotNull private final File myDbFile;

    static {
      ensureTempFolder();
    }

    public SqLiteJdbcWorkingCopyFormatOperation(@NotNull File dbFile) {
      myDbFile = dbFile;
    }

    @Nullable
    @Override
    public WorkingCopyFormat execute(boolean lastAttempt) {
      Connection connection = null;
      int userVersion = 0;

      try {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + FileUtil.toSystemIndependentName(myDbFile.getPath()));
        ResultSet resultSet = connection.createStatement().executeQuery("pragma user_version");

        if (resultSet.next()) {
          userVersion = resultSet.getInt(1);
        }
        else {
          LOG.info("No result while getting user version for " + myDbFile.getPath());
        }
      }
      catch (ClassNotFoundException | SQLException e) {
        LOG.info(e);
      }
      finally {
        close(connection);
      }

      WorkingCopyFormat format = WorkingCopyFormat.getInstance(userVersion);

      return !WorkingCopyFormat.UNKNOWN.equals(format) ? format : null;
    }

    private static void ensureTempFolder() {
      if (System.getProperty(SQLITE_JDBC_TEMP_DIR_PROPERTY) == null) {
        System.setProperty(SQLITE_JDBC_TEMP_DIR_PROPERTY, PathManager.getTempPath());
      }
    }

    private static void close(@Nullable Connection connection) {
      if (connection != null) {
        try {
          connection.close();
        }
        catch (SQLException e) {
          notifyDatabaseError();
        }
      }
    }
  }

  private static class SqLiteDb extends SqlJetDb {

    private SqLiteDb(@NotNull File file, boolean writable) {
      super(file, writable);
    }

    @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
    @NotNull
    public static SqLiteDb open(@NotNull File file, boolean write) throws SqlJetException {
      final SqLiteDb db = new SqLiteDb(file, write);
      db.open();
      return db;
    }

    private int getUserVersion() {
      int result = 0;
      ISqlJetOptions options = dbHandle.getOptions();

      if (options != null) {
        try {
          result = options.getUserVersion();
        }
        catch (SqlJetException ignore) {
        }
      }

      return result;
    }

    /**
     * See {@link SqlJetEngine#readSchema()} for reference.
     */
    @NotNull
    private String getDbSchema() {
      String result = "";

      try {
        result = (String)runSynchronized(engine -> {
          btree.enter();
          try {
            return readDbSchema();
          }
          finally {
            btree.leave();
          }
        });
      }
      catch (SqlJetException ignore) {
      }

      return result;
    }

    /**
     * See {@link SqlJetSchema#init()} for reference.
     */
    @NotNull
    private String readDbSchema() throws SqlJetException {
      StringBuilder result = new StringBuilder();
      ISqlJetBtreeSchemaTable table = new SqlJetBtreeSchemaTable(btree, false);

      try {
        table.lock();
        try {
          for (table.first(); !table.eof(); table.next()) {
            String sql = table.getSqlField();

            if (sql != null) {
              result.append(sql);
              result.append("\n");
            }
          }
        }
        finally {
          table.unlock();
        }
      }
      finally {
        table.close();
      }

      return result.toString();
    }
  }
}
