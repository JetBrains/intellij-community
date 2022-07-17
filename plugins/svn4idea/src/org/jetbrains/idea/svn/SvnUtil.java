// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.*;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.LockDialog;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.Status;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.SystemProperties.getUserHome;
import static com.intellij.util.containers.ContainerUtil.map2Array;
import static java.util.Collections.emptyList;

public final class SvnUtil {
  @NonNls public static final String SVN_ADMIN_DIR_NAME =
    SystemInfo.isWindows && EnvironmentUtil.getValue("SVN_ASP_DOT_NET_HACK") != null ? "_svn" : ".svn";
  @NonNls public static final String ENTRIES_FILE_NAME = "entries";
  @NonNls public static final String WC_DB_FILE_NAME = "wc.db";
  @NonNls public static final String PATH_TO_LOCK_FILE = SVN_ADMIN_DIR_NAME + "/lock";

  public static final NotNullLazyValue<Path> USER_CONFIGURATION_PATH = NotNullLazyValue.atomicLazy(() -> {
    return SystemInfo.isWindows
           ? Paths.get(Objects.requireNonNull(EnvironmentUtil.getValue("APPDATA")), "Subversion")
           : Paths.get(getUserHome(), ".subversion");
  });
  public static final NotNullLazyValue<Path> SYSTEM_CONFIGURATION_PATH = NotNullLazyValue.atomicLazy(() -> {
    return SystemInfo.isWindows
           ? Paths.get(Objects.requireNonNull(EnvironmentUtil.getValue("ALLUSERSPROFILE")), "Application Data", "Subversion")
           : Paths.get("/etc/subversion");
  });

  private static final Logger LOG = Logger.getInstance(SvnUtil.class);

  public static final Pattern ERROR_PATTERN = Pattern.compile("^svn: (E(\\d+)): (.*)$", Pattern.MULTILINE);
  public static final Pattern WARNING_PATTERN = Pattern.compile("^svn: warning: (W(\\d+)): (.*)$", Pattern.MULTILINE);

  private static final Pair<Url, WorkingCopyFormat> UNKNOWN_REPOSITORY_AND_FORMAT = Pair.create(null, WorkingCopyFormat.UNKNOWN);

  private static final @NonNls String NOT_VERSIONED_RESOURCE = "(not a versioned resource)";

  private SvnUtil() { }

  @Nullable
  public static String parseWarning(@NotNull String text) {
    Matcher matcher = WARNING_PATTERN.matcher(text);
    // currently treating only first warning
    return matcher.find() ? matcher.group() : null;
  }

  @Nullable
  public static Date parseDate(@Nullable String value) {
    return parseDate(value, true);
  }

  @Nullable
  public static Date parseDate(@Nullable String value, boolean logError) {
    if (value == null) return null;

    try {
      return Date.from(Instant.parse(value));
    }
    catch (DateTimeParseException | ArithmeticException e) {
      if (logError) {
        LOG.error("Could not parse date " + value, e);
      }
      return null;
    }
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
    Set<VirtualFile> result = new HashSet<>();
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
    ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(progress);
  }

  public static File @NotNull [] toIoFiles(VirtualFile @NotNull [] files) {
    return map2Array(files, File.class, VfsUtilCore::virtualToIoFile);
  }

  public static void doLockFiles(Project project, final SvnVcs activeVcs, final File @NotNull [] ioFiles) throws VcsException {
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
      @Override
      public void consume(ProgressEvent event) {
        if (event.getAction() == EventAction.LOCK_FAILED) {
          failedLocks.add(notNull(event.getErrorMessage(), event.getFile().getAbsolutePath()));
          count[0]--;
        }
      }

      @Override
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
      String[] failedFiles = ArrayUtilRt.toStringArray(failedLocks);
      List<VcsException> exceptions = new ArrayList<>();
      for (String file : failedFiles) {
        exceptions.add(new VcsException(SvnBundle.message("exception.text.locking.file.failed", file)));
      }
      @Nls StringBuilder sb = new StringBuilder(SvnBundle.message("message.text.files.lock.failed", failedFiles.length == 1 ? 0 : 1));
      for (VcsException vcsException : exceptions) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(vcsException.getMessage());
      }
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
      @Override
      public void consume(ProgressEvent event) {
        if (event.getAction() == EventAction.UNLOCK_FAILED) {
          failedUnlocks.add(notNull(event.getErrorMessage(), event.getFile().getAbsolutePath()));
          count[0]--;
        }
      }

      @Override
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
      String[] failedFiles = ArrayUtilRt.toStringArray(failedUnlocks);
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
  public static MultiMap<Pair<Url, WorkingCopyFormat>, Change> splitChangesIntoWc(@NotNull SvnVcs vcs, @NotNull List<? extends Change> changes) {
    return splitIntoRepositoriesMap(vcs, changes, change -> ChangesUtil.getFilePath(change));
  }

  @NotNull
  public static <T> MultiMap<Pair<Url, WorkingCopyFormat>, T> splitIntoRepositoriesMap(@NotNull final SvnVcs vcs,
                                                                                       @NotNull Collection<? extends T> items,
                                                                                       @NotNull final Convertor<? super T, ? extends FilePath> converter) {
    return ContainerUtil.groupBy(items, item -> {
      RootUrlInfo path = vcs.getSvnFileUrlMapping().getWcRootForFilePath(converter.convert(item));

      return path == null ? UNKNOWN_REPOSITORY_AND_FORMAT : Pair.create(path.getRepositoryUrl(), path.getFormat());
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
      result = FileUtilRt.doIOOperation(new SqLiteJdbcWorkingCopyFormatOperation(dbFile));

      if (result == null) {
        notifyDatabaseError();
      }
    }

    return result != null ? result : WorkingCopyFormat.UNKNOWN;
  }

  private static void notifyDatabaseError() {
    VcsBalloonProblemNotifier.NOTIFICATION_GROUP
      .createNotification(SvnBundle.message("notification.content.can.not.access.working.copy.database"), NotificationType.ERROR)
      .notify(null);
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
    return info != null ? info.getRepositoryId() : null;
  }

  @Nullable
  public static String getRepositoryUUID(final SvnVcs vcs, final Url url) {
    try {
      final Info info = vcs.getInfo(url, Revision.UNDEFINED);

      return (info == null) ? null : info.getRepositoryId();
    }
    catch (SvnBindException e) {
      return null;
    }
  }

  @Nullable
  public static Url getRepositoryRoot(final SvnVcs vcs, final File file) {
    final Info info = vcs.getInfo(file);
    return info != null ? info.getRepositoryRootUrl() : null;
  }

  @Nullable
  public static Url getRepositoryRoot(final SvnVcs vcs, final String url) {
    try {
      return getRepositoryRoot(vcs, createUrl(url));
    }
    catch (SvnBindException e) {
      return null;
    }
  }

  @Nullable
  public static Url getRepositoryRoot(final SvnVcs vcs, final Url url) throws SvnBindException {
    Info info = vcs.getInfo(url, Revision.HEAD);

    return (info == null) ? null : info.getRepositoryRootUrl();
  }

  public static boolean isWorkingCopyRoot(@NotNull File file) {
    return FileUtil.filesEqual(file, getWorkingCopyRoot(file));
  }

  public static boolean isWorkingCopyRoot(@NotNull VirtualFile file) {
    VirtualFile adminDir = file.findChild(SVN_ADMIN_DIR_NAME);
    return adminDir != null && adminDir.findChild(WC_DB_FILE_NAME) != null;
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
  public static Url getBranchForUrl(@NotNull SvnVcs vcs, @NotNull VirtualFile vcsRoot, @NotNull Url url) {
    Url result = null;
    SvnBranchConfigurationNew configuration = SvnBranchConfigurationManager.getInstance(vcs.getProject()).get(vcsRoot);

    try {
      result = configuration.getWorkingBranch(url);
    }
    catch (SvnBindException e) {
      LOG.debug(e);
    }

    return result;
  }

  public static boolean checkRepositoryVersion15(@NotNull SvnVcs vcs, @NotNull Url url) {
    // Merge info tracking is supported in repositories since svn 1.5 (June 2008) - see http://subversion.apache.org/docs/release-notes/.
    // But still some users use 1.4 repositories and currently we need to know if repository supports merge info for some code flows.

    boolean result = false;

    try {
      result = vcs.getFactory().createRepositoryFeaturesClient().supportsMergeTracking(url);
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

  public static boolean seemsLikeVersionedDir(@NotNull VirtualFile file) {
    final VirtualFile child = file.findChild(SVN_ADMIN_DIR_NAME);
    return child != null && child.isDirectory();
  }

  public static boolean seemsLikeVersionedDir(@NotNull File file) {
    return new File(file, SVN_ADMIN_DIR_NAME).isDirectory();
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
  public static Url getUrl(final SvnVcs vcs, final File file) {
    // todo for moved items?
    final Info info = vcs.getInfo(file);

    return info == null ? null : info.getUrl();
  }

  public static boolean remoteFolderIsEmpty(@NotNull SvnVcs vcs, @NotNull String url) throws VcsException {
    Target target = Target.on(createUrl(url));
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
  public static File getWorkingCopyRoot(@NotNull File file) {
    File current = getParentWithDb(file);
    if (current == null) return null;

    WorkingCopyFormat format = getFormat(current);

    return format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN) ? current : null;
  }

  @Nullable
  public static VirtualFile getWorkingCopyRoot(@NotNull VirtualFile file) {
    do {
      if (isWorkingCopyRoot(file)) return file;
      file = file.getParent();
    }
    while (file != null);

    return null;
  }

  @Nullable
  public static File getParentWithDb(@NotNull File file) {
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

  public static boolean isAncestor(@NotNull Url parentUrl, @NotNull Url childUrl) {
    return Url.isAncestor(parentUrl.toDecodedString(), childUrl.toDecodedString());
  }

  public static String getRelativeUrl(@NotNull Url parentUrl, @NotNull Url childUrl) {
    return getRelativeUrl(parentUrl.toDecodedString(), childUrl.toDecodedString());
  }

  public static String getRelativeUrl(@NotNull Target parent, @NotNull Target child) {
    return getRelativeUrl(toDecodedString(parent), toDecodedString(child));
  }

  private static String getRelativeUrl(@NotNull String parentUrl, @NotNull String childUrl) {
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
  public static String join(final String @NotNull ... parts) {
    return StringUtil.join(parts, "/");
  }

  @NotNull
  public static Url removePathTail(@NotNull Url url) throws SvnBindException {
    // TODO: Fix - remove tail only from path
    return createUrl(Url.removeTail(url.toDecodedString()), false);
  }

  @NotNull
  public static Revision getHeadRevision(@NotNull SvnVcs vcs, @NotNull Url url) throws SvnBindException {
    Info info = vcs.getInfo(url, Revision.HEAD);

    if (info == null) {
      throw new SvnBindException(SvnBundle.message("error.could.not.get.info.for.path", url));
    }
    if (!info.getRevision().isValid()) {
      throw new SvnBindException(SvnBundle.message("error.could.not.get.revision.for.url", url));
    }

    return info.getRevision();
  }

  public static byte[] getFileContents(@NotNull final SvnVcs vcs,
                                       @NotNull final Target target,
                                       @Nullable final Revision revision,
                                       @Nullable final Revision pegRevision)
    throws VcsException {
    return vcs.getFactory(target).createContentClient().getContent(target, revision, pegRevision);
  }

  @NotNull
  public static Url createUrl(@NotNull String url) throws SvnBindException {
    return createUrl(url, true);
  }

  @NotNull
  public static Url createUrl(@NotNull String url, boolean encoded) throws SvnBindException {
    return Url.parse(url, encoded);
  }

  @NotNull
  public static Url parseUrl(@NotNull String url) {
    try {
      return createUrl(url);
    }
    catch (SvnBindException e) {
      throw createIllegalArgument(e);
    }
  }

  @NotNull
  public static Url parseUrl(@NotNull String url, boolean encoded) {
    try {
      return createUrl(url, encoded);
    }
    catch (SvnBindException e) {
      throw createIllegalArgument(e);
    }
  }

  @NotNull
  public static Url append(@NotNull Url parent, @NotNull String child) throws SvnBindException {
    return append(parent, child, false);
  }

  @NotNull
  public static Url append(@NotNull Url parent, @NotNull String child, boolean encoded) throws SvnBindException {
    return parent.appendPath(child, encoded);
  }

  @NotNull
  public static IllegalArgumentException createIllegalArgument(@NotNull Exception e) {
    return new IllegalArgumentException(e);
  }

  @Nullable
  public static String getChangelistName(@NotNull final Status status) {
    // no explicit check on working copy format supports change lists as they are supported from svn 1.5
    // and anyway status.getChangeListName() should just return null if change lists are not supported.
    return status.getNodeKind().isFile() ? status.getChangeListName() : null;
  }

  public static boolean isUnversionedOrNotFound(@NotNull SvnBindException e) {
    return e.contains(ErrorCode.WC_PATH_NOT_FOUND) ||
           e.contains(ErrorCode.UNVERSIONED_RESOURCE) ||
           e.contains(ErrorCode.WC_NOT_WORKING_COPY) ||
           // thrown when getting info from repository for non-existent item - like HEAD revision for deleted file
           e.contains(ErrorCode.ILLEGAL_TARGET) ||
           // for svn 1.6
           StringUtil.containsIgnoreCase(e.getMessage(), NOT_VERSIONED_RESOURCE);
  }

  public static boolean isAuthError(@NotNull SvnBindException e) {
    return e.contains(ErrorCode.RA_NOT_AUTHORIZED) ||
           e.contains(ErrorCode.RA_UNKNOWN_AUTH) ||
           e.containsCategory(ErrorCategory.AUTHZ) ||
           e.containsCategory(ErrorCategory.AUTHN);
  }

  // TODO: Create custom Target class and implement append there
  @NotNull
  public static Target append(@NotNull Target target, @NotNull String path) throws SvnBindException {
    return append(target, path, false);
  }

  @NotNull
  public static Target append(@NotNull Target target, @NotNull String path, boolean checkAbsolute) throws SvnBindException {
    Target result;

    if (target.isFile()) {
      result = Target.on(resolvePath(target.getFile(), path));
    }
    else {
      result = Target.on(checkAbsolute && URI.create(path).isAbsolute() ? createUrl(path) : append(target.getUrl(), path));
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
   * {@code SvnTarget.getPathOrUrlDecodedString} does not correctly work for URL targets - {@code Url.toString} instead of
   * {@code Url.toDecodedString} is used.
   * <p/>
   * Current utility method fixes this case.
   */
  @NotNull
  public static String toDecodedString(@NotNull Target target) {
    return target.isFile() ? target.getFile().getPath() : target.getUrl().toDecodedString();
  }

  private static class SqLiteJdbcWorkingCopyFormatOperation
    implements FileUtilRt.RepeatableIOOperation<WorkingCopyFormat, RuntimeException> {

    private static final String SQLITE_JDBC_TEMP_DIR_PROPERTY = "org.sqlite.tmpdir";
    private static final @NonNls String USER_VERSION_QUERY = "pragma user_version";

    @NotNull private final File myDbFile;

    static {
      ensureTempFolder();
    }

    SqLiteJdbcWorkingCopyFormatOperation(@NotNull File dbFile) {
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
        ResultSet resultSet = connection.createStatement().executeQuery(USER_VERSION_QUERY);

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
}
