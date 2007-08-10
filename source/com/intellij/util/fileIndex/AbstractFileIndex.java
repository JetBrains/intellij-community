/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.fileIndex;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.*;

/**
 * @author nik
 */
public abstract class AbstractFileIndex<IndexEntry extends FileIndexEntry> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.fileIndex.AbstractFileIndex");
  private Map<String, IndexEntry> myFileUrl2IndexEntry = new HashMap<String, IndexEntry>();
  private ProjectFileIndex myProjectFileIndex;
  private boolean myFormatChanged;
  private final Project myProject;
  private AbstractFileIndex.FileIndexCacheUpdater myRootsChangeCacheUpdater;
  private StartupManagerEx myStartupManager;
  private FileIndexRefreshCacheUpdater myRefreshCacheUpdater;

  protected AbstractFileIndex(final Project project) {
    myProject = project;
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myStartupManager = StartupManagerEx.getInstanceEx(project);
  }

  protected abstract IndexEntry createIndexEntry(DataInputStream input) throws IOException;


  protected abstract String getLoadingIndicesMessage();

  protected abstract String getBuildingIndicesMessage(boolean formatChanged);

  public abstract boolean belongs(VirtualFile file);


  public abstract byte getCurrentVersion();

  @NonNls
  public abstract String getCachesDirName();

  public abstract void queueEntryUpdate(final VirtualFile file);

  protected abstract void doUpdateIndexEntry(final VirtualFile file);

  public ProjectFileIndex getProjectFileIndex() {
    return myProjectFileIndex;
  }

  protected File getCacheLocation(final String dirName) {
    final String cacheFileName = myProject.getName() + "." + myProject.getLocationHash();
    return new File(PathManager.getSystemPath() + File.separator + dirName + File.separator + cacheFileName);
  }

  public final void updateIndexEntry(final VirtualFile file) {
    if (!myStartupManager.startupActivityPassed() || myProjectFileIndex.isIgnored(file)) {
      return;
    }
    
    doUpdateIndexEntry(file);
  }

  public final void removeIndexEntry(final VirtualFile file) {
    if (myProjectFileIndex.isIgnored(file)) {
      return;
    }

    removeIndexEntry(file.getUrl());
  }


  protected void onEntryAdded(String url, IndexEntry entry) {
  }

  protected void onEntryRemoved(String url, IndexEntry entry) {
  }

  private void saveCache() {
    final File cacheFile = getCacheLocation(getCachesDirName());
    FileUtil.createParentDirs(cacheFile);
    DataOutputStream output = null;
    try {
      cacheFile.createNewFile();
      output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile)));
      output.writeByte(getCurrentVersion());
      writeHeader(output);
      output.writeInt(myFileUrl2IndexEntry.size());
      for (final Map.Entry<String, IndexEntry> entry : myFileUrl2IndexEntry.entrySet()) {
        output.writeUTF(entry.getKey());
        entry.getValue().write(output);
      }
      output.close();
    }
    catch (IOException e) {
      LOG.debug(e);
      if (output != null) {
        try {
          output.close();
          output = null;
        }
        catch (IOException e1) {
        }
      }
      cacheFile.delete();
    } finally {
      if (output != null) {
        try {
          output.close();
        }
        catch (IOException e1) {
        }
      }
    }
  }

  protected void readHeader(DataInputStream input) throws IOException {
  }

  protected void writeHeader(final DataOutputStream output) throws IOException {
  }

  private boolean loadCache() {
    final File cacheFile = getCacheLocation(getCachesDirName());
    if (!cacheFile.exists()) return false;
    clearMaps();

    DataInputStream input = null;
    final ProgressIndicator indicator = getProgressIndicator();
    try {
      input = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)));
      int version = input.readByte();
      if (version != getCurrentVersion()) {
        myFormatChanged = true;
        return false;
      }

      if (indicator != null) {
        indicator.pushState();
        indicator.setText(getLoadingIndicesMessage());
      }

      readHeader(input);

      int size = input.readInt();
      for (int i = 0; i < size; i++) {
        if (indicator != null) {
          indicator.setFraction(((double)i) / size);
        }
        final String url = input.readUTF();
        putIndexEntry(url, createIndexEntry(input));
      }
      if (indicator != null) {
        indicator.popState();
      }

      input.close();
      return true;
    }
    catch (IOException e) {
      LOG.debug(e);
    } finally {
      if (input != null) {
        try {
          input.close();
        }
        catch (IOException e1) {
        }
      }
    }
    return false;
  }

  public final void putIndexEntry(final String url, final IndexEntry entry) {
    myFileUrl2IndexEntry.put(url, entry);
    onEntryAdded(url, entry);
  }

  public final IndexEntry getIndexEntry(final String url) {
    return myFileUrl2IndexEntry.get(url);
  }

  @Nullable
  public final IndexEntry removeIndexEntry(final String url) {
    final IndexEntry entry = myFileUrl2IndexEntry.remove(url);
    if (entry != null) {
      onEntryRemoved(url, entry);
    }
    return entry;
  }

  protected void clearMaps() {
    myFileUrl2IndexEntry.clear();
  }

  public void initialize() {
    final Runnable loadCacheRunnable = new Runnable() {
      public void run() {
        myRootsChangeCacheUpdater = new FileIndexCacheUpdater();
        final ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(myProject);
        rootManager.registerChangeUpdater(myRootsChangeCacheUpdater);
        loadCache();
        buildIndex();
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          myRefreshCacheUpdater = new FileIndexRefreshCacheUpdater(AbstractFileIndex.this);
        }
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myRefreshCacheUpdater = new FileIndexRefreshCacheUpdater(this);
    }

    StartupManager.getInstance(myProject).registerStartupActivity(loadCacheRunnable);

  }

  @Nullable
  private static ProgressIndicator getProgressIndicator() {
    return ProgressManager.getInstance().getProgressIndicator();
  }

  public void dispose() {
    if (myRefreshCacheUpdater != null) {
      myRefreshCacheUpdater.dispose();
    }
    if (myRootsChangeCacheUpdater != null) {
      ProjectRootManagerEx.getInstanceEx(myProject).unregisterChangeUpdater(myRootsChangeCacheUpdater);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      getCacheLocation(getCachesDirName()).delete();
    }
    else {
      saveCache();
    }
    clearMaps();
  }

  private void buildIndex() {
    final ProgressIndicator indicator = getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setIndeterminate(false);
      indicator.setText(getBuildingIndicesMessage(myFormatChanged));
      myFormatChanged = false;
    }

    final VirtualFile[] files = queryNeededFiles(true, getFileTypesToRefresh());
    for (int i = 0; i < files.length; i++) {
      if (indicator != null) {
        indicator.setFraction(((double)i)/ files.length);
      }
      doUpdateIndexEntry(files[i]);
    }

    if (indicator != null) {
      indicator.popState();
    }
  }

  @Nullable
  protected Set<FileType> getFileTypesToRefresh() {
    return null;
  }

  protected void setFormatChanged() {
    myFormatChanged = true;
  }

  private VirtualFile[] queryNeededFiles(final boolean includeChangedFiles, @Nullable Set<FileType> fileTypesToRefresh) {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    myProjectFileIndex.iterateContent(new ContentIterator() {
      public boolean processFile(VirtualFile fileOrDir) {
        if (belongs(fileOrDir)) {
          files.add(fileOrDir);
        }
        return true;
      }
    });

    List<VirtualFile> toUpdate = new ArrayList<VirtualFile>();
    Set<String> toRemove = new THashSet<String>(myFileUrl2IndexEntry.keySet());

    final int size = files.size();
    for (int i = 0; i < size; i++) {
      final VirtualFile file = files.get(i);
      final String url = file.getUrl();
      final IndexEntry entry = myFileUrl2IndexEntry.get(url);
      toRemove.remove(url);
      if (entry == null
          || includeChangedFiles && entry.getTimeStamp() != file.getTimeStamp()
          || fileTypesToRefresh != null && fileTypesToRefresh.contains(file.getFileType())) {
        toUpdate.add(file);
      }
    }

    for (String url : toRemove) {
      removeIndexEntry(url);
    }

    return toUpdate.toArray(new VirtualFile[toUpdate.size()]);
  }

  private class FileIndexCacheUpdater implements CacheUpdater {
    public VirtualFile[] queryNeededFiles() {
      return AbstractFileIndex.this.queryNeededFiles(false, null);
    }

    public void processFile(FileContent fileContent) {
      updateIndexEntry(fileContent.getVirtualFile());
    }

    public void updatingDone() {
    }

    public void canceled() {
    }
  }
}
