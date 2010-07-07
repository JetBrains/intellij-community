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
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.dialogs.LockDialog;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.*;

public class SvnUtil {
  @NonNls public static final String SVN_ADMIN_DIR_NAME = SVNFileUtil.getAdminDirectoryName();
  @NonNls public static final String ENTRIES_FILE_NAME = "entries";
  @NonNls public static final String DIR_PROPS_FILE_NAME = "dir-props";
  @NonNls public static final String PATH_TO_LOCK_FILE = SVN_ADMIN_DIR_NAME + "/lock";
  @NonNls public static final String LOCK_FILE_NAME = "lock";

  private SvnUtil() {
  }

  public static void crawlWCRoots(VirtualFile path, NotNullFunction<VirtualFile, Collection<VirtualFile>> callback) {
    if (path == null) {
      return;
    }
    final boolean isDirectory = path.isDirectory();
    final VirtualFile parentVFile = (!isDirectory) || (!path.exists()) ? path.getParent() : path;
    if (parentVFile == null) {
      return;
    }
    final File parent = new File(parentVFile.getPath());

    if (SVNWCUtil.isVersionedDirectory(parent)) {
      checkCanceled();
      final Collection<VirtualFile> pending = callback.fun(path);
      checkCanceled();
      for (VirtualFile virtualFile : pending) {
        crawlWCRoots(virtualFile, callback);
      }
    }
    else if (isDirectory) {
      checkCanceled();
      VirtualFile[] children = path.getChildren();
      for (int i = 0; children != null && i < children.length; i++) {
        checkCanceled();
        VirtualFile child = children[i];
        if (child.isDirectory()) {
          crawlWCRoots(child, callback);
        }
      }
    }
  }

  public static Collection<File> crawlWCRoots(File path, SvnWCRootCrawler callback, ProgressIndicator progress) {
    final Collection<File> result = new HashSet<File>();
    File parent = path.isFile() || !path.exists() ? path.getParentFile() : path;
    if (SVNWCUtil.isVersionedDirectory(parent)) {
      checkCanceled(progress);
      final Collection<File> pending = callback.handleWorkingCopyRoot(path, progress);
      checkCanceled(progress);
      for (final File aPending : pending) {
        result.addAll(crawlWCRoots(aPending, callback, progress));
      }
      result.add(path);
    }
    else if (path.isDirectory()) {
      checkCanceled(progress);
      File[] children = path.listFiles();
      for (int i = 0; children != null && i < children.length; i++) {
        checkCanceled(progress);
        File child = children[i];
        if (child.isDirectory()) {
          result.addAll(crawlWCRoots(child, callback, progress));
        }
      }
    }
    return result;
  }

  private static void checkCanceled() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    checkCanceled(indicator);
  }

  private static void checkCanceled(final ProgressIndicator progress) {
    if (progress != null && progress.isCanceled()) {
      throw new ProcessCanceledException();
    }
  }

  public static String[] getLocationsForModule(final SvnVcs vcs, File path, ProgressIndicator progress) {
    LocationsCrawler crawler = new LocationsCrawler(vcs);
    crawlWCRoots(path, crawler, progress);
    return crawler.getLocations();
  }

  public static Map<String, File> getLocationInfoForModule(final SvnVcs vcs, File path, ProgressIndicator progress) {
    final LocationsCrawler crawler = new LocationsCrawler(vcs);
    crawlWCRoots(path, crawler, progress);
    return crawler.getLocationInfos();
  }

  public static void doLockFiles(Project project, final SvnVcs activeVcs, final File[] ioFiles) throws VcsException {
    final String lockMessage;
    final boolean force;
    // TODO[yole]: check for shift pressed
    if (activeVcs.getCheckoutOptions().getValue()) {
      LockDialog dialog = new LockDialog(project, true, ioFiles != null && ioFiles.length > 1);
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
            File file = ioFile;
            if (progress != null) {
              progress.setText2(SvnBundle.message("progress.text2.processing.file", file.getName()));
            }
            wcClient.doLock(new File[]{file}, force, lockMessage);
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
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, SvnBundle.message("message.title.lock.failures"));
    }

    WindowManager.getInstance().getStatusBar(project).setInfo(SvnBundle.message("message.text.files.locked", count[0]));
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
            File file = ioFile;
            if (progress != null) {
              progress.setText2(SvnBundle.message("progress.text2.processing.file", file.getName()));
            }
            wcClient.doUnlock(new File[]{file}, force);
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

    WindowManager.getInstance().getStatusBar(project).setInfo(SvnBundle.message("message.text.files.unlocked", count[0]));
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
  }

  public static String formatRepresentation(final WorkingCopyFormat format) {
    if (WorkingCopyFormat.ONE_DOT_SIX.equals(format)) {
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

  private static class LocationsCrawler implements SvnWCRootCrawler {
    private final SvnVcs myVcs;
    private final Map<String, File> myLocations;

    public LocationsCrawler(SvnVcs vcs) {
      myVcs = vcs;
      myLocations = new HashMap<String, File>();
    }

    public String[] getLocations() {
      final Set<String> set = myLocations.keySet();
      return ArrayUtil.toStringArray(set);
    }

    public Map<String, File> getLocationInfos() {
      return Collections.unmodifiableMap(myLocations);
    }

    public Collection<File> handleWorkingCopyRoot(File root, ProgressIndicator progress) {
      final Collection<File> result = new HashSet<File>();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.discovering.location", root.getAbsolutePath()));
      }
      try {
        SVNWCClient wcClient = myVcs.createWCClient();
        SVNInfo info = wcClient.doInfo(root, SVNRevision.WORKING);
        if (info != null && info.getURL() != null) {
          myLocations.put(info.getURL().toString(), info.getFile());
        }
      }
      catch (SVNException e) {
        //
      }
      return result;
    }
  }

  @Nullable
  public static String getRepositoryUUID(final SvnVcs vcs, final File file) {
    final SVNWCClient client = vcs.createWCClient();
    try {
      final SVNInfo info = client.doInfo(file, SVNRevision.WORKING);
      return (info == null) ? null : info.getRepositoryUUID();
    } catch (SVNException e) {
      return null;
    }
  }

  @Nullable
  public static String getRepositoryUUID(final SvnVcs vcs, final SVNURL url) {
    final SVNWCClient client = vcs.createWCClient();
    try {
      final SVNInfo info = client.doInfo(url, SVNRevision.WORKING, SVNRevision.WORKING);
      return (info == null) ? null : info.getRepositoryUUID();
    } catch (SVNException e) {
      return null;
    }
  }

  @Nullable
  public static SVNURL getRepositoryRoot(final SvnVcs vcs, final File file) {
    final SVNWCClient client = vcs.createWCClient();
    try {
      final SVNInfo info = client.doInfo(file, SVNRevision.WORKING);
      return (info == null) ? null : info.getRepositoryRootURL();
    } catch (SVNException e) {
      return null;
    }
  }

  @Nullable
  public static SVNURL getRepositoryRoot(final SvnVcs vcs, final String url) {
    try {
      return getRepositoryRoot(vcs, SVNURL.parseURIEncoded(url));
    }
    catch (SVNException e) {
      return null;
    }
  }

  @Nullable
  public static SVNURL getRepositoryRoot(final SvnVcs vcs, final SVNURL url) {
    final SVNWCClient client = vcs.createWCClient();
    try {
      SVNInfo info = client.doInfo(url, SVNRevision.UNDEFINED, SVNRevision.HEAD);
      return (info == null) ? null : info.getRepositoryRootURL();
    } catch (SVNException e) {
      return null;
    }
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

    try {
      return SVNWCUtil.getWorkingCopyRoot(file, true);
    } catch (SVNException e) {
      return null;
    }
  }

  @Nullable
  public static SVNURL getWorkingCopyUrl(final SvnVcs vcs, final File file) {
    try {
      if(SVNWCUtil.isWorkingCopyRoot(file)) {
        final SVNWCClient client = vcs.createWCClient();
        final SVNInfo info = client.doInfo(file, SVNRevision.WORKING);
        return info.getURL();
      }
    } catch (SVNException e) {
      //
    }
    return null;
  }

  public static File fileFromUrl(final File baseDir, final String baseUrl, final String fullUrl) throws SVNException {
    assert fullUrl.startsWith(baseUrl);

    final String part = fullUrl.substring(baseUrl.length()).replace('/', File.separatorChar).replace('\\', File.separatorChar);
    return new File(baseDir, part);
  }

  public static VirtualFile getVirtualFile(final String filePath) {
    @NonNls final String path = VfsUtil.pathToUrl(filePath.replace(File.separatorChar, '/'));
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
  public static VirtualFile correctRoot(final Project project, final String path) {
    if (path.length() == 0) {
      // project root
      return project.getBaseDir();
    }
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  @Nullable
  public static VirtualFile correctRoot(final Project project, final VirtualFile file) {
    if (file.getPath().length() == 0) {
      // project root
      return project.getBaseDir();
    }
    return file;
  }

  public static boolean isOneDotFiveAvailable(final Project project, final RepositoryLocation location) {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final List<WCInfo> infos = vcs.getAllWcInfos();

    for (WCInfo info : infos) {
      if (! info.getFormat().supportsMergeInfo()) {
        continue;
      }

      final String url = info.getUrl().toString();
      if ((location != null) && (! location.toPresentableString().startsWith(url)) &&
          (! url.startsWith(location.toPresentableString()))) {
        continue;
      }
      if (! checkRepositoryVersion15(vcs, url)) {
        continue;
      }
      return true;
    }
    return false;
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
      final SVNInfo svnInfo = client.doInfo(file, SVNRevision.WORKING);
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

  public static boolean isAdminDirectory(File parent, final String name) {
    if (name.equals(SVN_ADMIN_DIR_NAME)) {
      return true;
    }
    if (parent != null) {
      if (parent.getName().equals(SVN_ADMIN_DIR_NAME)) {
        return true;
      }
      parent = parent.getParentFile();
      if (parent != null && parent.getName().equals(SVN_ADMIN_DIR_NAME)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isAdminDirectory(VirtualFile parent, String name) {
    // never allow to delete admin directories by themselves (this can happen during LVCS undo,
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
  public static SVNURL getUrl(final File file) {
    SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
    try {
      wcAccess.probeOpen(file, false, 0);
      SVNEntry entry = wcAccess.getVersionedEntry(file, false);
      return entry.getSVNURL();
    } catch (SVNException e) {
      //
    } finally {
      try {
        wcAccess.close();
      }
      catch (SVNException e) {
        //
      }
    }
    return null;
  }
}
